package tech.genna.TY3.tests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("attempt-result")
class AttemptResultApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    final List<Map<String, Object>> exchanges = new ArrayList<>();
    private String base;
    private String caseName;
    private boolean passed;

    @BeforeEach
    void prepare(TestInfo info) throws Exception {
        caseName = info.getDisplayName();
        assertEquals("true", System.getenv("API_TEST_DISPOSABLE"), "Use the disposable test stand");
        assertTrue(System.getenv().getOrDefault("API_TEST_VERSION", "").matches("[a-fA-F0-9]{7,40}"),
                "Set API_TEST_VERSION to the running backend revision");
        base = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:18083");
        for (String address : List.of(base)) {
            URI uri = URI.create(address);
            assertEquals("http", uri.getScheme());
            assertTrue(Set.of("localhost", "127.0.0.1").contains(uri.getHost()));
            assertNull(uri.getUserInfo());
            assertNull(uri.getQuery());
            assertNull(uri.getFragment());
            assertTrue(Set.of("", "/").contains(uri.getPath()));
        }
    }

    @AfterEach
    void record() {
        CASES.add(Map.of("case", caseName, "result", passed ? "PASSED" : "FAILED", "requests", exchanges));
    }

    @AfterAll
    static void report() throws Exception {
        Path path = Path.of("build/reports/attempt-result-http.json");
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), Map.of(
                "revision", System.getenv().getOrDefault("API_TEST_VERSION", "unknown"),
                "baseUrl", System.getenv().getOrDefault("API_BASE_URL", "http://localhost:18083"),
                "environment", "local HTTP backend, PostgreSQL, S3, controlled ML",
                "finishedAt", Instant.now().toString(), "cases", CASES));
    }

    @ParameterizedTest(name = "Result: {0}, {1} questions")
    @CsvSource({"SINGLE_CHOICE,5", "TRUE_FALSE,10", "MULTIPLE_CHOICE,5"})
    void resultAndAccessProtection(String type, int count) throws Exception {
        String owner = register();
        String stranger = register();
        String material = upload(owner);
        String quiz = generate(owner, material, type, count);
        JsonNode started = start(owner, quiz);
        String attempt = started.path("attempt").path("id").asText();
        assertFalse(attempt.isBlank());
        String path = "/api/attempts/" + attempt;
        JsonNode questions = started.path("questions");
        assertEquals(count, questions.size());
        noKeys(started);
        noKeys(call("GET", path, owner, null, 200));
        JsonNode activeError = call("GET", path + "/result", owner, null, 409);
        error(activeError, "RESULT_NOT_READY");
        assertEquals("IN_PROGRESS", call("GET", path, owner, null, 200).path("attempt").path("status").asText());

        // The test-only ML fixture has fixed answer positions. Public API never supplies the key.
        for (int i = 0; i < count - 1; i++) {
            JsonNode q = questions.get(i);
            List<String> selections = new ArrayList<>();
            if (i == count - 2) {
                selections.add(q.path("options").get(type.equals("TRUE_FALSE") ? (i % 2 == 0 ? 1 : 0) : 3).path("id").asText());
            } else {
                selections.add(q.path("options").get(type.equals("TRUE_FALSE") ? i % 2 : 0).path("id").asText());
                if (type.equals("MULTIPLE_CHOICE") && i != 0) selections.add(q.path("options").get(1).path("id").asText());
            }
            call("PUT", path + "/answers/" + q.path("id").asText(), owner,
                    Map.of("selectedOptionIds", selections), 200);
        }
        noKeys(call("GET", path, owner, null, 200));
        JsonNode completion = call("POST", path + "/complete", owner, null, 200);
        JsonNode result = call("GET", path + "/result", owner, null, 200);
        assertEquals(attempt, result.path("attemptId").asText());
        assertEquals(quiz, result.path("quiz").path("id").asText());
        assertEquals(count, result.path("totalQuestions").asInt());
        int expectedCorrect = type.equals("MULTIPLE_CHOICE") ? count - 3 : count - 2;
        assertEquals(expectedCorrect, result.path("correctCount").asInt());
        assertEquals(count - expectedCorrect - 1, result.path("incorrectCount").asInt());
        assertEquals(1, result.path("skippedCount").asInt());
        assertEquals(expectedCorrect, result.path("score").asInt());
        assertEquals(100.0 * expectedCorrect / count, result.path("scorePercent").asDouble(), 0.001);
        assertTrue(result.path("durationSeconds").asLong(-1) >= 0);
        Instant.parse(result.path("completedAt").asText());
        completion.properties().forEach(entry -> assertEquals(entry.getValue(), result.path(entry.getKey()), entry.getKey()));
        assertEquals(count, result.path("items").size());
        JsonNode savedAnswers = call("GET", path, owner, null, 200).path("answers");
        for (int i = 0; i < count; i++) {
            JsonNode item = result.path("items").get(i);
            assertEquals(questions.get(i), item.path("question"));
            JsonNode options = questions.get(i).path("options");
            List<String> expectedKeys = new ArrayList<>();
            expectedKeys.add(options.get(type.equals("TRUE_FALSE") ? i % 2 : 0).path("id").asText());
            if (type.equals("MULTIPLE_CHOICE")) expectedKeys.add(options.get(1).path("id").asText());
            assertEquals(JSON.valueToTree(expectedKeys), item.path("correctOptionIds"));
            boolean fullAnswer = i < count - 2 && !(type.equals("MULTIPLE_CHOICE") && i == 0);
            assertEquals(fullAnswer ? "CORRECT" : i == count - 1 ? "SKIPPED" : "INCORRECT", item.path("outcome").asText());
            assertEquals(fullAnswer ? 1 : 0, item.path("score").asInt());
            JsonNode expectedSelection = JSON.createArrayNode();
            for (JsonNode answer : savedAnswers) {
                if (answer.path("questionId").equals(questions.get(i).path("id"))) expectedSelection = answer.path("selectedOptionIds");
            }
            assertEquals(expectedSelection, item.path("selectedOptionIds"));
        }
        error(call("GET", path + "/result", null, null, 401), "AUTHENTICATION_REQUIRED");
        JsonNode foreign = call("GET", path + "/result", stranger, null, 404);
        JsonNode missing = call("GET", "/api/attempts/" + UUID.randomUUID() + "/result", owner, null, 404);
        error(foreign, "ATTEMPT_NOT_FOUND");
        error(missing, "ATTEMPT_NOT_FOUND");
        assertEquals(foreign, missing);
        JsonNode review = call("GET", path + "/review", owner, null, 404);
        noKeys(review);
        assertFalse(review.has("items"));

        JsonNode repeated = start(owner, quiz);
        String second = repeated.path("attempt").path("id").asText();
        assertNotEquals(attempt, second);
        assertEquals(0, repeated.path("answers").size());
        assertEquals(result, call("GET", path + "/result", owner, null, 200));
        call("POST", "/api/attempts/" + second + "/complete", owner, null, 200);
        JsonNode secondResult = call("GET", "/api/attempts/" + second + "/result", owner, null, 200);
        assertEquals(count, secondResult.path("skippedCount").asInt());
        assertEquals(0, secondResult.path("score").asInt());
        assertEquals(result, call("GET", path + "/result", owner, null, 200));
        passed = true;
    }

    static void noKeys(JsonNode node) {
        if (node.isObject()) node.properties().forEach(entry -> {
            assertFalse(Set.of("correctOptionIds", "correctAnswer", "isCorrect", "score", "scorePercent", "outcome",
                    "explanation", "sourceId", "isSkipped", "correctCount", "incorrectCount", "skippedCount").contains(entry.getKey()),
                    "Sensitive field in public/error response: " + entry.getKey());
            noKeys(entry.getValue());
        });
        if (node.isArray()) node.forEach(AttemptResultApiTest::noKeys);
    }

    private static void error(JsonNode node, String code) {
        assertEquals(code, node.path("code").asText());
        assertTrue(Set.of("code", "message", "fieldErrors").containsAll(fieldNames(node)));
        assertTrue(node.path("fieldErrors").isMissingNode() || node.path("fieldErrors").isNull() || node.path("fieldErrors").isEmpty());
        noKeys(node);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private JsonNode start(String token, String quiz) throws Exception {
        return call("POST", "/api/quizzes/" + quiz + "/attempts", token, null, 201);
    }

    String register() throws Exception {
        String name = "result_" + UUID.randomUUID().toString().replace("-", "");
        return call("POST", "/api/auth/register", null, Map.of("username", name,
                "email", name + "@example.org", "password", UUID.randomUUID() + "Qa1!"), 200).path("token").asText();
    }

    String upload(String token) throws Exception {
        var req = RestAssured.given().baseUri(base).header("Authorization", "Bearer " + token)
                .multiPart("metadata", "metadata.json", JSON.writeValueAsBytes(Map.of("name", "Result QA lecture", "subjectName", "QA")), "application/json")
                .multiPart("file", "lecture.txt", Files.readAllBytes(Path.of("src/test/resources/quiz-crud/lecture.txt")), "text/plain");
        Response response = req.post("/api/materials");
        exchanges.add(Map.of("method", "POST", "path", "/api/materials", "expected", 201, "actual", response.statusCode()));
        assertEquals(201, response.statusCode());
        return JSON.readTree(response.asByteArray()).path("material").path("id").asText();
    }

    String generate(String token, String material, String type, int count) throws Exception {
        String job = call("POST", "/api/quizzes", token, Map.of("materialId", material, "name", "Result QA",
                "questionCount", count, "difficulty", "MEDIUM", "questionType", type), 202).path("id").asText();
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            JsonNode state = call("GET", "/api/generation-jobs/" + job, token, null, 200);
            if (state.path("status").asText().equals("COMPLETED")) return state.path("quizId").asText();
            assertTrue(Set.of("QUEUED", "RUNNING").contains(state.path("status").asText()), "Generation failed");
            Thread.sleep(250);
        }
        return fail("Generation timed out");
    }

    private JsonNode call(String method, String path, String token, Object payload, int expected) throws Exception {
        var req = RestAssured.given().baseUri(base).redirects().follow(false)
                .config(RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10000).setParam("http.socket.timeout", 30000)));
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (method.equals("POST")) req.header("Idempotency-Key", UUID.randomUUID().toString());
        if (payload != null) req.contentType("application/json").body(JSON.writeValueAsBytes(payload));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("method", method);
        row.put("path", path);
        row.put("expected", expected);
        exchanges.add(row);
        Response response;
        try { response = req.request(method, path); }
        catch (Exception ex) { row.put("actual", "unavailable"); throw new AssertionError("HTTP request failed: " + method + " " + path); }
        row.put("actual", response.statusCode());
        assertEquals(expected, response.statusCode(), method + " " + path);
        if (expected == 200 && path.endsWith("/result")) {
            assertTrue(response.header("Cache-Control").contains("no-store"));
        }
        return JSON.readTree(response.asByteArray());
    }
}
