package tech.genna.TY3.ml;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.service.generation.GenerationWorker;
import tech.genna.TY3.service.generation.GenerationParameters;
import tech.genna.TY3.service.generation.MlGenerator;
import org.springframework.boot.test.context.SpringBootTest;
import tech.genna.TY3.support.QuizFixtures;
import tech.genna.TY3.tests.base.BaseTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL + real backend HTTP + real generator/SSE adapter, with a local deterministic model provider. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.config.import=", "generation.worker-delay-ms=3600000"})
@Import(QuizCrudIntegrationTest.ModelConfiguration.class)
class QuizCrudIntegrationTest extends BaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MlGenerator ml;
    @Autowired GenerationWorker worker;
    @Autowired TestModelServer model;
    private final List<UUID> owners = new ArrayList<>(), jobs = new ArrayList<>();
    private record Session(UUID id, String token) {}

    @BeforeEach
    void resetProvider() {
        model.invalidCount = false;
        model.calls.set(0);
    }

    @AfterEach
    void removeOnlyTestFixtures() {
        jdbc.execute("ALTER TABLE generation_jobs DROP CONSTRAINT IF EXISTS test_reject_completed");
        for (UUID owner : owners) {
            jdbc.update("DELETE FROM quizzes WHERE user_id = ?", owner);
            jdbc.update("DELETE FROM generation_jobs WHERE user_id = ?", owner);
            jdbc.update("DELETE FROM materials WHERE user_id = ?", owner);
            jdbc.update("DELETE FROM subjects WHERE user_id = ?", owner);
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
        }
        jobs.forEach(job -> jdbc.update("DELETE FROM generation_settings_snapshots WHERE job_id = ?", job));
    }

    @Test
    void generatedQuizCanBeReadRenamedAndDeletedWithoutLeakingAnswers() {
        var owner = register();
        UUID material = material(owner.id());
        var saved = generate(owner, material, "  Original  ", QuestionType.SINGLE_CHOICE);
        UUID id = saved.getId();
        assertEquals("Original", saved.getName());
        assertEquals(2, model.calls.get(), "Generation and content review must use the real adapter");
        assertEquals(1, count("SELECT count(*) FROM quizzes WHERE user_id = ?", owner.id()));
        assertEquals(5, count("SELECT jsonb_array_length(result_json::jsonb->'questions') FROM quizzes WHERE id = ?", id));
        assertEquals(5, count("SELECT sum(jsonb_array_length(q->'correctOptionIds')) FROM quizzes, jsonb_array_elements(result_json::jsonb->'questions') q WHERE id = ?", id));

        var list = call(owner, "GET", "/api/quizzes", null).expect(200);
        assertEquals(1, list.size("items"));
        assertEquals(id.toString(), list.string("items[0].id"));
        assertEquals("TXT", list.string("items[0].format"));
        assertEquals("SINGLE_CHOICE", list.string("items[0].questionType"));
        assertEquals("Исходный материал", list.string("items[0].materialName"));
        assertNoAnswers(list);

        var detail = call(owner, "GET", "/api/quizzes/" + id, null).expect(200);
        assertEquals(5, detail.integer("settings.questionCount"));
        assertEquals(5, detail.size("questions"));
        assertEquals(4, detail.size("questions[0].options"));
        String firstQuestion = detail.string("questions[0].id");
        UUID.fromString(firstQuestion);
        UUID.fromString(detail.string("questions[0].options[0].id"));
        assertNoAnswers(detail);

        call(owner, "PATCH", "/api/quizzes/" + id, Map.of("name", "  Renamed  ")).expect(200);
        var renamed = call(owner, "GET", "/api/quizzes/" + id, null).expect(200);
        assertEquals("Renamed", renamed.string("quiz.name"));
        assertEquals(firstQuestion, renamed.string("questions[0].id"));
        assertEquals(saved.getCreatedAt().toInstant(), java.time.OffsetDateTime.parse(renamed.string("quiz.createdAt")).toInstant());
        assertEquals("Renamed", call(owner, "GET", "/api/quizzes", null).expect(200).string("items[0].name"));

        call(owner, "DELETE", "/api/quizzes/" + id, null).expect(204);
        call(owner, "GET", "/api/quizzes/" + id, null).expect(404);
        call(owner, "DELETE", "/api/quizzes/" + id, null).expect(404);
        assertEquals(0, call(owner, "GET", "/api/quizzes", null).expect(200).size("items"));
        assertEquals(0, count("SELECT count(*) FROM quizzes WHERE id = ?", id));
        assertEquals(1, count("SELECT count(*) FROM materials WHERE id = ?", material));
        assertEquals(1, count("SELECT count(*) FROM generation_settings_snapshots WHERE job_id = ?", jobs.getFirst()));
    }

    @Test
    void ownerIsolationValidationAndUniquenessUseRealHttp() {
        var owner = register();
        var stranger = register();
        UUID material = material(owner.id());
        UUID id = generate(owner, material, "Name", QuestionType.MULTIPLE_CHOICE).getId();
        var other = generate(owner, material, "Other", QuestionType.TRUE_FALSE);
        assertEquals(QuestionType.TRUE_FALSE, other.getQuestionType());

        for (UUID unavailable : List.of(id, UUID.randomUUID())) {
            call(stranger, "GET", "/api/quizzes/" + unavailable, null).expect(404);
            call(stranger, "PATCH", "/api/quizzes/" + unavailable, Map.of("name", "Changed")).expect(404);
            call(stranger, "DELETE", "/api/quizzes/" + unavailable, null).expect(404);
        }
        assertEquals(0, call(stranger, "GET", "/api/quizzes", null).expect(200).size("items"));
        call(null, "GET", "/api/quizzes", null).expect(401);
        call(null, "GET", "/api/quizzes/" + id, null).expect(401);
        call(null, "DELETE", "/api/quizzes/" + id, null).expect(401);
        call(null, "PATCH", "/api/quizzes/" + id, Map.of("name", "Name")).expect(401);

        for (var body : List.of(Map.of("name", "  "), Map.of("name", "x".repeat(101)),
                Map.of("name", "Name", "questionCount", 10), Map.of("difficulty", "HARD"))) {
            call(owner, "PATCH", "/api/quizzes/" + id, body).expect(400);
        }
        call(owner, "PATCH", "/api/quizzes/" + id, Map.of("name", "Other")).expect(409);
        call(owner, "PATCH", "/api/quizzes/" + id, Map.of("name", "Name")).expect(200);
        call(owner, "PATCH", "/api/quizzes/" + id, Map.of("name", "other")).expect(200);
        call(owner, "PATCH", "/api/quizzes/" + id, Map.of("name", " " + "Я".repeat(100) + " ")).expect(200);
        assertEquals(2, count("SELECT count(*) FROM quizzes WHERE user_id = ?", owner.id()));

        int before = model.calls.get();
        call(stranger, "POST", "/api/quizzes", Map.of("materialId", material.toString(), "name", "Foreign", "questionCount", 5, "difficulty", "MEDIUM", "questionType", "SINGLE_CHOICE")).expect(404);
        assertEquals(before, model.calls.get(), "Foreign material must not be sent to the model");
    }

    @Test
    void sourceAndSettingsRemainSnapshotsAfterLaterEdits() {
        var owner = register();
        UUID material = material(owner.id());
        UUID id = generate(owner, material, "History", QuestionType.SINGLE_CHOICE).getId();
        jdbc.update("UPDATE materials SET title = 'Changed material' WHERE id = ?", material);
        jdbc.update("UPDATE generation_options SET available = FALSE WHERE category = 'QUESTION_COUNT' AND option_value = '5'");
        try {
            var detail = call(owner, "GET", "/api/quizzes/" + id, null).expect(200);
            assertEquals("Исходный материал", detail.string("quiz.materialName"));
            assertEquals(5, detail.integer("settings.questionCount"));
            jdbc.update("DELETE FROM materials WHERE id = ?", material);
            call(owner, "GET", "/api/quizzes/" + id, null).expect(200);
        } finally {
            jdbc.update("UPDATE generation_options SET available = TRUE WHERE category = 'QUESTION_COUNT' AND option_value = '5'");
        }
    }

    @Test
    void rejectedMlOutputCreatesNoQuiz() {
        var owner = register();
        UUID material = material(owner.id());
        model.invalidCount = true;
        assertThrows(tech.genna.TY3.service.generation.GenerationFailure.class, () -> generate(owner, material, "Invalid", QuestionType.SINGLE_CHOICE));
        assertEquals(0, count("SELECT count(*) FROM quizzes WHERE user_id = ?", owner.id()));
    }

    @Test
    void databaseFailureRollsBackRootQuestionsAndOptions() {
        var owner = register();
        UUID material = material(owner.id());
        jdbc.execute("ALTER TABLE generation_jobs ADD CONSTRAINT test_reject_completed CHECK (status <> 'COMPLETED' OR user_id <> '" + owner.id() + "')");
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> generate(owner, material, "Rollback", QuestionType.SINGLE_CHOICE));
        assertEquals(0, count("SELECT count(*) FROM quizzes WHERE user_id = ?", owner.id()));
        assertEquals("QUEUED", call(owner, "GET", "/api/generation-jobs/" + jobs.getFirst(), null).expect(200).string("status"));
    }

    private QuizSummary generate(Session owner, UUID material, String name, QuestionType type) {
        var request = new GenerateQuizRequest(material, QuestionCount.NUMBER_5, Difficulty.MEDIUM, type).name(name);
        UUID job = UUID.fromString(call(owner, "POST", "/api/quizzes", Map.of("materialId", material.toString(),
                "name", name, "questionCount", 5, "difficulty", "MEDIUM", "questionType", type.getValue())).expect(202).string("id"));
        jobs.add(job);
        var result = ml.generate(job, "Учебный материал с положениями для теста.", GenerationParameters.from(request), event -> {
            if (event.stage() == GenerationEvent.Stage.VALIDATED)
                assertEquals(0, count("SELECT count(*) FROM quizzes WHERE job_id = ?", job));
        });
        worker.accept(job, result);
        assertEquals("COMPLETED", call(owner, "GET", "/api/generation-jobs/" + job, null).expect(200).string("status"));
        return JsonMapper.builder().build().readValue(call(owner, "GET", "/api/quizzes/" + job, null).expect(200).at("quiz").toString(), QuizSummary.class);
    }

    private Session register() {
        String name = "quiz_" + UUID.randomUUID().toString().replace("-", "");
        String token = call(null, "POST", "/api/auth/register", Map.of("username", name,
                "email", name + "@example.test", "password", "integration-only-password")).expect(200).string("token");
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, name);
        owners.add(id);
        return new Session(id, token);
    }

    private UUID material(UUID owner) {
        UUID subject = UUID.randomUUID(), material = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects(id, user_id, name) VALUES (?, ?, 'Databases')", subject, owner);
        jdbc.update("""
                INSERT INTO materials(id, user_id, subject_id, title, file_id, original_filename,
                    file_type, size_bytes, status, raw_text, created_at)
                VALUES (?, ?, ?, 'Исходный материал', ?, 'lecture.txt', 'TXT', 100, 'STORED', 'Учебный текст', CURRENT_TIMESTAMP)
                """, material, owner, subject, UUID.randomUUID());
        return material;
    }

    private int count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args));
    }

    private HttpResult call(Session owner, String method, String path, Object body) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        builder.header("Idempotency-Key", UUID.randomUUID().toString());
        if (owner != null) builder.header("Authorization", "Bearer " + owner.token());
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (IOException ex) {
            throw new AssertionError("Backend HTTP failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new AssertionError(ex);
        }
    }

    private record HttpResult(int status, String body) {
        HttpResult expect(int expected) { assertEquals(expected, status, body); return this; }
        JsonNode at(String path) {
            return JsonMapper.builder().build().readTree(body).at("/" + path.replaceAll("\\[(\\d+)\\]", "/$1").replace(".", "/"));
        }
        String string(String path) { return at(path).asText(); }
        int integer(String path) { return at(path).intValue(); }
        int size(String path) { return at(path).size(); }
    }

    private static void assertNoAnswers(HttpResult response) {
        for (String secret : List.of("correctOptionIds", "isCorrect", "is_correct", "explanation", "generatedTitle")) {
            assertFalse(response.body().contains(secret), "Public API must exclude " + secret);
        }
    }

    @TestConfiguration
    static class ModelConfiguration {
        @Bean(destroyMethod = "close")
        TestModelServer testModelServer() throws IOException { return new TestModelServer(); }

        @Bean
        @Primary
        QuizGenerator deterministicGenerator(TestModelServer server) {
            return new QuizGenerator(new OpenRouterClient("integration-key", "test/model:free",
                    URI.create("http://127.0.0.1:" + server.server.getAddress().getPort() + "/chat/completions"),
                    Duration.ofSeconds(10), 12000), 60000);
        }
    }

    static final class TestModelServer implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean invalidCount;

        TestModelServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                try (exchange) {
                    calls.incrementAndGet();
                    var payload = Json.read(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var messages = (List<?>) payload.get("messages");
                    var user = Json.read((String) ((Map<?, ?>) messages.get(1)).get("content"));
                    String answer;
                    if (user.containsKey("settings")) {
                        var settings = (Map<?, ?>) user.get("settings");
                        var material = (Map<?, ?>) user.get("material");
                        var request = new GenerationRequest((String) user.get("requestId"), (String) material.get("id"),
                                (String) material.get("text"), ((Number) settings.get("questionCount")).intValue(),
                                GenerationRequest.Difficulty.valueOf((String) settings.get("difficulty")),
                                GenerationRequest.QuestionType.valueOf((String) settings.get("questionType")));
                        var quiz = QuizFixtures.valid(request);
                        if (invalidCount) quiz = new Quiz(2, quiz.materialId(), quiz.title(), quiz.questions().subList(0, 4));
                        answer = Json.write(Map.of("requestId", request.requestId(), "status", "success", "quiz", quiz));
                    } else {
                        answer = Json.write(Map.of("verdict", "pass", "issues", List.of()));
                    }
                    String event = Json.write(Map.of("choices", List.of(Map.of("delta", Map.of("content", answer), "finish_reason", "stop"))));
                    byte[] body = ("data: " + event + "\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
        }

        @Override public void close() { server.stop(0); }
    }
}
