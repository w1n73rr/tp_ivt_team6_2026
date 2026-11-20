package tech.genna.TY3.tests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("attempt-passing")
class AttemptPassingApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private final AttemptResultApiTest fixture = new AttemptResultApiTest();
    private final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();
    private String base;
    private String name;
    private boolean passed;

    @BeforeEach
    void prepare(TestInfo info) throws Exception {
        fixture.prepare(info);
        base = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:18083");
        name = info.getDisplayName();
    }

    @AfterEach
    void audit() {
        var all = new ArrayList<>(fixture.exchanges);
        all.addAll(requests);
        CASES.add(Map.of("case", name, "result", passed ? "PASSED" : "FAILED", "requests", all));
    }

    @AfterAll
    static void report() throws Exception {
        Path path = Path.of("build/reports/attempt-passing-http.json");
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), Map.of(
                "backendRevision", System.getenv().getOrDefault("API_TEST_VERSION", "unknown"),
                "baseUrl", System.getenv().getOrDefault("API_BASE_URL", "http://localhost:18083"),
                "finishedAt", Instant.now().toString(), "mode", "real HTTP/SQL/S3, controlled ML for setup only",
                "result", CASES.stream().allMatch(c -> c.get("result").equals("PASSED")) ? "PASSED" : "FAILED",
                "cases", CASES));
    }

    @ParameterizedTest(name = "PASS lifecycle: {0}, {1} questions")
    @CsvSource({"SINGLE_CHOICE,5", "SINGLE_CHOICE,10", "MULTIPLE_CHOICE,5", "MULTIPLE_CHOICE,10", "TRUE_FALSE,5", "TRUE_FALSE,10"})
    void startResumeChangeClearAndComplete(String type, int count) throws Exception {
        String owner = fixture.register();
        String quiz = fixture.generate(owner, fixture.upload(owner), type, count);
        JsonNode originalQuiz = send("GET", "/api/quizzes/" + quiz, owner, null, null, 200).data();
        String key = UUID.randomUUID().toString();
        JsonNode first = start(owner, quiz, key, 201);
        String id = id(first);
        String path = attempt(id);
        JsonNode questions = first.path("questions");
        assertPublic(first);
        assertEquals(originalQuiz.path("questions"), questions);
        assertEquals(count, questions.size());
        assertEquals(0, first.path("answers").size());
        for (int i = 0; i < count; i++) {
            assertEquals(i + 1, questions.get(i).path("position").asInt());
            assertEquals(type, questions.get(i).path("type").asText());
            assertEquals(type.equals("TRUE_FALSE") ? 2 : 4, questions.get(i).path("options").size());
        }
        assertEquals(first, start(owner, quiz, key, 200));
        JsonNode q = questions.get(0);
        List<String> correct = correct(q, type, 0);
        String answerPath = path + "/answers/" + q.path("id").asText();
        JsonNode saved = save(owner, answerPath, correct);
        assertEquals(saved, save(owner, answerPath, correct));
        List<String> changed = List.of(option(q, type.equals("TRUE_FALSE") ? 1 : 2));
        save(owner, answerPath, changed);
        assertEquals(JSON.valueToTree(changed), answer(read(owner, id), q).path("selectedOptionIds"));
        save(owner, answerPath, List.of());
        JsonNode cleared = read(owner, id);
        assertEquals(0, answer(cleared, q).path("selectedOptionIds").size());
        assertEquals(0, cleared.path("attempt").path("answeredCount").asInt());
        assertEquals(1, cleared.path("currentPosition").asInt());
        assertEquals(cleared, start(owner, quiz, UUID.randomUUID().toString(), 200));
        save(owner, answerPath, correct);
        for (int i = 1; i < count - 1; i++) {
            JsonNode question = questions.get(i);
            List<String> selected = i == count - 2 ? List.of(option(question, type.equals("TRUE_FALSE") ? 1 - i % 2 : 3)) : correct(question, type, i);
            save(owner, path + "/answers/" + question.path("id").asText(), selected);
        }
        JsonNode continued = read(owner, id);
        assertEquals(count - 1, continued.path("attempt").path("answeredCount").asInt());
        assertEquals(count, continued.path("currentPosition").asInt());
        assertEquals(continued, start(owner, quiz, UUID.randomUUID().toString(), 200));
        assertError(send("GET", path + "/result", owner, null, null, 409).data(), "RESULT_NOT_READY");
        JsonNode result = send("POST", path + "/complete", owner, null, null, 200).data();
        assertEquals(id, result.path("attemptId").asText());
        assertTotals(result, count, count - 2, 1, 1);
        assertEquals(result, send("POST", path + "/complete", owner, null, null, 200).data());
        assertEquals("COMPLETED", send("GET", path, owner, null, null, 200).data().path("attempt").path("status").asText());
        JsonNode snapshot = send("GET", path + "/result", owner, null, null, 200).data();
        for (List<String> selected : List.of(correct, changed, List.<String>of())) {
            assertError(send("PUT", answerPath, owner, Map.of("selectedOptionIds", selected), null, 409).data(), "ATTEMPT_CLOSED");
        }
        JsonNode replay = start(owner, quiz, key, 200);
        assertPublic(replay);
        assertEquals(id, id(replay));
        JsonNode repeated = start(owner, quiz, UUID.randomUUID().toString(), 201);
        assertNotEquals(id, id(repeated));
        assertEquals(0, repeated.path("answers").size());
        assertEquals(snapshot, send("GET", path + "/result", owner, null, null, 200).data());
        assertEquals(originalQuiz, send("GET", "/api/quizzes/" + quiz, owner, null, null, 200).data());
        passed = true;
    }

    @ParameterizedTest(name = "PASS multiple-choice scoring: {0}")
    @CsvSource({"0+1,1", "1+0,1", "0,0", "0+2,0", "0+1+2,0", "3,0", "empty,0"})
    void exactSetScoring(String indices, int expected) throws Exception {
        String owner = fixture.register();
        String quiz = fixture.generate(owner, fixture.upload(owner), "MULTIPLE_CHOICE", 5);
        JsonNode started = start(owner, quiz, UUID.randomUUID().toString(), 201);
        JsonNode q = started.path("questions").get(0);
        List<String> selections = new ArrayList<>();
        if (!indices.equals("empty")) for (String index : indices.split("\\+")) selections.add(option(q, Integer.parseInt(index)));
        save(owner, attempt(id(started)) + "/answers/" + q.path("id").asText(), selections);
        JsonNode result = send("POST", attempt(id(started)) + "/complete", owner, null, null, 200).data();
        int skipped = selections.isEmpty() ? 5 : 4;
        assertTotals(result, 5, expected, 5 - skipped - expected, skipped);
        passed = true;
    }

    @Test
    void rejectsAnonymousForeignMissingAndUnrelatedIds() throws Exception {
        String owner = fixture.register();
        String stranger = fixture.register();
        String quizA = fixture.generate(owner, fixture.upload(owner), "SINGLE_CHOICE", 5);
        String quizB = fixture.generate(stranger, fixture.upload(stranger), "SINGLE_CHOICE", 5);
        JsonNode a = start(owner, quizA, UUID.randomUUID().toString(), 201);
        JsonNode b = start(stranger, quizB, UUID.randomUUID().toString(), 201);
        String path = attempt(id(a));
        JsonNode q = a.path("questions").get(0);
        String answerPath = path + "/answers/" + q.path("id").asText();
        Map<String, Object> selection = Map.of("selectedOptionIds", List.of(option(q, 0)));
        for (String token : new String[]{null, "invalid-token"}) {
            assertError(send("POST", "/api/quizzes/" + quizA + "/attempts", token, null, UUID.randomUUID().toString(), 401).data(), "AUTHENTICATION_REQUIRED");
            assertError(send("GET", path, token, null, null, 401).data(), "AUTHENTICATION_REQUIRED");
            assertError(send("PUT", answerPath, token, selection, null, 401).data(), "AUTHENTICATION_REQUIRED");
            assertError(send("POST", path + "/complete", token, null, null, 401).data(), "AUTHENTICATION_REQUIRED");
        }
        JsonNode foreignQuiz = send("POST", "/api/quizzes/" + quizB + "/attempts", owner, null, UUID.randomUUID().toString(), 404).data();
        JsonNode missingQuiz = send("POST", "/api/quizzes/" + UUID.randomUUID() + "/attempts", owner, null, UUID.randomUUID().toString(), 404).data();
        assertError(foreignQuiz, "QUIZ_NOT_FOUND");
        assertEquals(foreignQuiz, missingQuiz);
        for (String method : List.of("GET", "PUT", "POST")) {
            String suffix = method.equals("PUT") ? "/answers/" + q.path("id").asText() : method.equals("POST") ? "/complete" : "";
            Object body = method.equals("PUT") ? selection : null;
            JsonNode foreign = send(method, path + suffix, stranger, body, null, 404).data();
            JsonNode missing = send(method, attempt(UUID.randomUUID().toString()) + suffix, owner, body, null, 404).data();
            assertError(foreign, "ATTEMPT_NOT_FOUND");
            assertEquals(foreign, missing);
            assertError(send(method, attempt(id(b)) + suffix, owner, body, null, 404).data(), "ATTEMPT_NOT_FOUND");
        }
        String foreignQuestion = b.path("questions").get(0).path("id").asText();
        for (String question : List.of(foreignQuestion, UUID.randomUUID().toString())) {
            assertError(send("PUT", path + "/answers/" + question, owner, selection, null, 404).data(), "QUESTION_NOT_FOUND");
        }
        String foreignOption = option(b.path("questions").get(0), 0);
        String otherQuestionOption = option(a.path("questions").get(1), 0);
        List<Object> invalid = new ArrayList<>(List.of(List.of(foreignOption), List.of(otherQuestionOption), List.of(UUID.randomUUID().toString()),
                List.of(option(q, 0), option(q, 1)), List.of(option(q, 0), option(q, 0)), "wrong-type"));
        invalid.add(Arrays.asList((String) null));
        invalid.add(null);
        for (Object value : invalid) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("selectedOptionIds", value);
            assertError(send("PUT", answerPath, owner, payload, null, 400).data(), "VALIDATION_ERROR", "INVALID_ANSWER");
        }
        assertError(send("PUT", answerPath, owner, Map.of(), null, 400).data(), "VALIDATION_ERROR", "INVALID_ANSWER");
        assertEquals(a, read(owner, id(a)));
        assertEquals(b, read(stranger, id(b)));
        JsonNode publicQuiz = send("GET", "/api/quizzes/" + quizA, owner, null, null, 200).data();
        AttemptResultApiTest.noKeys(publicQuiz);
        passed = true;
    }

    @Test
    void parallelStartsAndRequestKeyReplayDoNotDuplicateAttempts() throws Exception {
        String owner = fixture.register();
        String material = fixture.upload(owner);
        String quiz = fixture.generate(owner, material, "SINGLE_CHOICE", 5);
        send("PATCH", "/api/quizzes/" + quiz, owner, Map.of("name", "First passing QA"), null, 200);
        String anotherQuiz = fixture.generate(owner, material, "SINGLE_CHOICE", 5);
        String path = "/api/quizzes/" + quiz + "/attempts";
        assertError(send("POST", path, owner, null, null, 400).data(), "VALIDATION_ERROR", "INVALID_IDEMPOTENCY_KEY");
        assertError(send("POST", path, owner, null, "invalid", 400).data(), "VALIDATION_ERROR", "INVALID_IDEMPOTENCY_KEY");
        String sharedKey = UUID.randomUUID().toString();
        var gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Reply>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                String key = i < 3 ? sharedKey : UUID.randomUUID().toString();
                futures.add(pool.submit(() -> { gate.await(); return send("POST", path, owner, null, key, 200, 201); }));
            }
            gate.countDown();
            Set<String> ids = new HashSet<>();
            int created = 0;
            for (Future<Reply> future : futures) {
                Reply reply = future.get(35, TimeUnit.SECONDS);
                assertPublic(reply.data());
                ids.add(id(reply.data()));
                if (reply.status() == 201) created++;
            }
            assertEquals(1, created);
            assertEquals(1, ids.size());
            String id = ids.iterator().next();
            assertError(send("POST", "/api/quizzes/" + anotherQuiz + "/attempts", owner, null, sharedKey, 409).data(), "IDEMPOTENCY_KEY_REUSED");
            JsonNode completed = send("POST", attempt(id) + "/complete", owner, null, null, 200).data();
            assertTotals(completed, 5, 0, 0, 5);
            assertEquals(id, id(start(owner, quiz, sharedKey, 200)));
            assertNotEquals(id, id(start(owner, quiz, UUID.randomUUID().toString(), 201)));
        } finally { pool.shutdownNow(); }
        passed = true;
    }

    @RepeatedTest(3)
    void answerAndCompletionRaceUsesCommittedSnapshot() throws Exception {
        String owner = fixture.register();
        String quiz = fixture.generate(owner, fixture.upload(owner), "SINGLE_CHOICE", 5);
        JsonNode started = start(owner, quiz, UUID.randomUUID().toString(), 201);
        String path = attempt(id(started));
        JsonNode q = started.path("questions").get(0);
        Map<String, Object> payload = Map.of("selectedOptionIds", List.of(option(q, 0)));
        var gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Reply> answer = pool.submit(() -> { gate.await(); return send("PUT", path + "/answers/" + q.path("id").asText(), owner, payload, null, 200, 409); });
            Future<Reply> completion = pool.submit(() -> { gate.await(); return send("POST", path + "/complete", owner, null, null, 200); });
            gate.countDown();
            Reply saved = answer.get(35, TimeUnit.SECONDS);
            JsonNode completed = completion.get(35, TimeUnit.SECONDS).data();
            boolean accepted = saved.status() == 200;
            if (accepted) assertPublic(saved.data());
            else assertError(saved.data(), "ATTEMPT_CLOSED");
            assertTotals(completed, 5, accepted ? 1 : 0, 0, accepted ? 4 : 5);
            JsonNode result = send("GET", path + "/result", owner, null, null, 200).data();
            assertEquals(accepted ? JSON.valueToTree(List.of(option(q, 0))) : JSON.createArrayNode(), result.path("items").get(0).path("selectedOptionIds"));
            assertEquals(completed, send("POST", path + "/complete", owner, null, null, 200).data());
            assertError(send("PUT", path + "/answers/" + q.path("id").asText(), owner, payload, null, 409).data(), "ATTEMPT_CLOSED");
            assertEquals(result, send("GET", path + "/result", owner, null, null, 200).data());
        } finally { pool.shutdownNow(); }
        passed = true;
    }

    @ParameterizedTest(name = "PASS parallel completion: {0} questions")
    @CsvSource({"5", "10"})
    void parallelCompletionReturnsOneStableResult(int count) throws Exception {
        String owner = fixture.register();
        String quiz = fixture.generate(owner, fixture.upload(owner), "TRUE_FALSE", count);
        JsonNode started = start(owner, quiz, UUID.randomUUID().toString(), 201);
        String path = attempt(id(started));
        for (int i = 0; i < count; i++) {
            JsonNode q = started.path("questions").get(i);
            save(owner, path + "/answers/" + q.path("id").asText(), correct(q, "TRUE_FALSE", i));
        }
        var gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Reply> first = pool.submit(() -> { gate.await(); return send("POST", path + "/complete", owner, null, null, 200); });
            Future<Reply> second = pool.submit(() -> { gate.await(); return send("POST", path + "/complete", owner, null, null, 200); });
            gate.countDown();
            JsonNode result = first.get(35, TimeUnit.SECONDS).data();
            assertEquals(result, second.get(35, TimeUnit.SECONDS).data());
            assertEquals(result, send("POST", path + "/complete", owner, null, null, 200).data());
            assertTotals(result, count, count, 0, 0);
        } finally { pool.shutdownNow(); }
        passed = true;
    }

    static void assertPublic(JsonNode node) {
        AttemptResultApiTest.noKeys(node);
        if (node.has("questions")) {
            fields(node, Set.of("attempt", "questions", "answers", "currentPosition"));
            fields(node.path("attempt"), Set.of("id", "quiz", "status", "startedAt", "answeredCount"));
            for (JsonNode q : node.path("questions")) {
                fields(q, Set.of("id", "type", "text", "position", "options"));
                for (JsonNode option : q.path("options")) fields(option, Set.of("id", "text"));
            }
            for (JsonNode saved : node.path("answers")) fields(saved, Set.of("questionId", "selectedOptionIds", "savedAt"));
        } else if (node.has("questionId")) fields(node, Set.of("questionId", "selectedOptionIds", "savedAt"));
    }

    private static void fields(JsonNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "Unexpected public response fields");
    }

    private static void assertError(JsonNode node, String... codes) {
        assertTrue(Arrays.asList(codes).contains(node.path("code").asText()), "Unexpected error code");
        assertTrue(Set.of("code", "message", "fieldErrors").containsAll(node.properties().stream().map(Map.Entry::getKey).toList()));
        AttemptResultApiTest.noKeys(node);
    }

    static void assertTotals(JsonNode node, int total, int correct, int incorrect, int skipped) {
        for (String field : List.of("totalQuestions", "correctCount", "incorrectCount", "skippedCount", "score")) {
            assertTrue(node.path(field).isIntegralNumber(), "Missing or non-integer field: " + field);
        }
        assertTrue(node.path("scorePercent").isNumber());
        assertEquals(total, node.path("totalQuestions").asInt());
        assertEquals(correct, node.path("correctCount").asInt());
        assertEquals(incorrect, node.path("incorrectCount").asInt());
        assertEquals(skipped, node.path("skippedCount").asInt());
        assertEquals(total, correct + incorrect + skipped);
        assertEquals(correct, node.path("score").asInt());
        assertEquals(100.0 * correct / total, node.path("scorePercent").asDouble(), 0.005);
    }

    private JsonNode read(String owner, String id) throws Exception {
        JsonNode reply = send("GET", attempt(id), owner, null, null, 200).data();
        assertPublic(reply);
        return reply;
    }

    private JsonNode save(String owner, String path, List<String> ids) throws Exception {
        JsonNode reply = send("PUT", path, owner, Map.of("selectedOptionIds", ids), null, 200).data();
        assertPublic(reply);
        return reply;
    }

    private JsonNode start(String owner, String quiz, String key, int expected) throws Exception {
        JsonNode reply = send("POST", "/api/quizzes/" + quiz + "/attempts", owner, null, key, expected).data();
        assertPublic(reply);
        return reply;
    }

    private static String id(JsonNode detail) { return detail.path("attempt").path("id").asText(); }
    private static String attempt(String id) { return "/api/attempts/" + id; }
    private static String option(JsonNode q, int index) { return q.path("options").get(index).path("id").asText(); }
    private static JsonNode answer(JsonNode detail, JsonNode question) {
        for (JsonNode answer : detail.path("answers")) if (answer.path("questionId").equals(question.path("id"))) return answer;
        return fail("Saved answer not found");
    }
    private static List<String> correct(JsonNode q, String type, int index) {
        if (type.equals("MULTIPLE_CHOICE")) return List.of(option(q, 0), option(q, 1));
        return List.of(option(q, type.equals("TRUE_FALSE") ? index % 2 : 0));
    }

    private Reply send(String method, String path, String token, Object data, String key, int... expected) throws Exception {
        var row = new LinkedHashMap<String, Object>();
        row.put("method", method);
        row.put("path", path);
        row.put("expected", expected);
        requests.add(row);
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        if (data != null) builder.header("Content-Type", "application/json");
        builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(data)));
        HttpResponse<byte[]> response;
        try { response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray()); }
        catch (Exception ex) { row.put("actual", "HTTP unavailable"); throw new AssertionError("HTTP failed: " + method + " " + path); }
        row.put("actual", response.statusCode());
        assertTrue(Arrays.stream(expected).anyMatch(status -> status == response.statusCode()), method + " " + path + " returned " + response.statusCode());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store") || response.statusCode() >= 400 || !(method.equals("GET") || path.endsWith("/attempts")),
                "Successful attempt/quiz response must disable caching");
        return new Reply(response.statusCode(), JSON.readTree(response.body()));
    }

    private record Reply(int status, JsonNode data) {}
}
