package tech.genna.TY3.tests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("generation-api")
@Execution(ExecutionMode.SAME_THREAD)
class GenerationApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String QUIZZES = "/api/quizzes";
    private static final String PARAMETERS = "/api/generation/parameters";
    private static final String JOBS = "/api/generation-jobs/";
    private static final Set<String> PENDING = Set.of("QUEUED", "RUNNING");
    private static final Set<String> TYPES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private static final String REVISION = System.getenv().getOrDefault("API_TEST_VERSION", "");
    private static boolean liveRun;

    private final List<Map<String, Object>> requests = new ArrayList<>();
    private String base;
    private String control;
    private String caseName;
    private boolean passed;

    @BeforeEach
    void setUp(TestInfo info) throws Exception {
        caseName = info.getDisplayName();
        liveRun = info.getTags().contains("generation-live");
        assertEquals("true", System.getenv("API_TEST_DISPOSABLE"), "Нужен одноразовый тестовый стенд");
        assertTrue(REVISION.matches("[a-fA-F0-9]{7,40}"), "Укажите SHA проверяемого Backend в API_TEST_VERSION");
        base = localUrl("API_BASE_URL", liveRun ? "http://localhost:18081" : "http://localhost:18083");
        if (liveRun) {
            assertEquals("true", System.getenv("API_TEST_LIVE_ML"), "Реальный запрос к модели требует явного запуска");
            assertNotEquals(18083, URI.create(base).getPort(), "Запустите сценарий на обычном Backend");
        } else {
            control = localUrl("API_TEST_CONTROL_URL", "http://localhost:18084");
            mode("SUCCESS");
        }
    }

    @AfterEach
    void finish() throws Exception {
        try {
            if (control != null) mode("SUCCESS");
        } finally {
            CASES.add(Map.of("case", caseName, "result", passed ? "PASSED" : "FAILED", "requests", requests));
        }
    }

    @AfterAll
    static void report() throws Exception {
        Path path = Path.of("build/reports", liveRun ? "generation-api-live-http.json" : "generation-api-http.json");
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), Map.of("task", "tpivt6_26-67",
                "revision", REVISION, "mode", liveRun ? "live" : "controlled", "finishedAt", Instant.now().toString(),
                "result", CASES.stream().allMatch(c -> c.get("result").equals("PASSED")) ? "PASSED" : "FAILED",
                "cases", CASES));
    }

    @Test
    @DisplayName("API-01: справочник параметров и запрет анонимного чтения")
    void checksParameters() throws Exception {
        expect("GET", PARAMETERS, 401, request(base, null));
        String owner = register();
        JsonNode catalog = body(expect("GET", PARAMETERS, 200, request(base, owner)));
        assertEquals("SINGLE", catalog.path("questionTypeSelection").asText());
        assertOptions(catalog.path("questionCounts"), Set.of("5", "10"));
        assertOptions(catalog.path("difficulties"), DIFFICULTIES);
        assertOptions(catalog.path("questionTypes"), TYPES);
        JsonNode defaults = catalog.path("defaults");
        assertDefaultAvailable(catalog.path("questionCounts"), defaults.path("questionCount"));
        assertDefaultAvailable(catalog.path("difficulties"), defaults.path("difficulty"));
        assertDefaultAvailable(catalog.path("questionTypes"), defaults.path("questionType"));
        passed = true;
    }

    @Test
    @DisplayName("API-02: валидация параметров, ключа и владельца до запуска ML")
    void rejectsInvalidRequestsBeforeGeneration() throws Exception {
        String owner = register();
        String other = register();
        String material = upload(owner, "TXT");
        String foreign = upload(other, "TXT");
        var valid = settings(material, 5, "MEDIUM", "SINGLE_CHOICE");
        JsonNode before = mode("SUCCESS");
        expect("POST", QUIZZES, 401, startRequest(null, valid, UUID.randomUUID().toString()));
        expect("POST", QUIZZES, 404, startRequest(other, valid, UUID.randomUUID().toString()));
        expect("POST", QUIZZES, 404, startRequest(owner, settings(UUID.randomUUID().toString(), 5,
                "MEDIUM", "SINGLE_CHOICE"), UUID.randomUUID().toString()));
        expect("POST", QUIZZES, 400, startRequest(owner, valid, null));
        expect("POST", QUIZZES, 400, startRequest(owner, valid, "not-a-uuid"));
        for (int count : List.of(0, 4, 6, 11)) reject(owner, changed(valid, "questionCount", count));
        reject(owner, changed(valid, "questionCount", 5.5));
        reject(owner, changed(valid, "difficulty", "IMPOSSIBLE"));
        reject(owner, changed(valid, "questionType", "UNKNOWN"));
        reject(owner, changed(valid, "questionType", List.of("SINGLE_CHOICE", "TRUE_FALSE")));
        reject(owner, changed(valid, "questionTypes", List.of("SINGLE_CHOICE", "TRUE_FALSE")));
        reject(owner, changed(valid, "materialId", List.of(material, foreign)));
        for (String required : List.of("materialId", "questionCount", "difficulty", "questionType")) {
            var missing = new LinkedHashMap<>(valid);
            missing.remove(required);
            reject(owner, missing);
        }
        assertTrue(before.equals(mode("SUCCESS")), "Некорректные запросы не создают задач и не вызывают ML");
        assertEmptyList(owner);
        assertEmptyList(other);
        passed = true;
    }

    @ParameterizedTest(name = "API-03: {0}, {1}, {2}, {3}")
    @CsvSource({"TXT,5,HARD,TRUE_FALSE", "PDF,5,EASY,SINGLE_CHOICE", "DOCX,10,MEDIUM,MULTIPLE_CHOICE"})
    void formatsPersistWithOneJob(String format, int count, String difficulty, String type) throws Exception {
        String owner = register();
        String other = register();
        String material = upload(owner, format);
        Map<String, Object> input = settings(material, count, difficulty, type);
        String key = UUID.randomUUID().toString();
        JsonNode before = mode("WAIT");
        String id = start(owner, input, key);
        JsonNode pending = status(owner, id);
        assertTrue(PENDING.contains(pending.path("status").asText()), "До ответа модели задача должна быть активна");
        assertEquals(id, start(owner, input, key), "Повтор запроса вернул другую задачу");
        int otherCount = count == 5 ? 10 : 5;
        expect("POST", QUIZZES, 409, startRequest(owner, changed(input, "questionCount", otherCount), key));
        expect("GET", JOBS + id, 401, request(base, null));
        expect("GET", JOBS + id, 404, request(base, other));
        expect("GET", JOBS + UUID.randomUUID(), 404, request(base, owner));
        assertEmptyList(owner);
        mode("SUCCESS");
        JsonNode terminal = awaitTerminal(owner, id);
        verifySaved(owner, material, format, count, difficulty, type, terminal);
        assertEquals(id, start(owner, input, key), "Повтор после завершения вернул другую задачу");
        verifySaved(owner, material, format, count, difficulty, type, status(owner, id));
        JsonNode after = mode("SUCCESS");
        assertDelta(before, after, "jobs", 1);
        assertDelta(before, after, "calls", 1);
        assertDelta(before, after, "quizzes", 1);
        passed = true;
    }

    @ParameterizedTest(name = "API-04: сбой {0} → {1}")
    @CsvSource({"EXTRACT,DOCUMENT_UNREADABLE,0", "ML_UNAVAILABLE,MODEL_UNAVAILABLE,1",
            "INVALID,INVALID_MODEL_OUTPUT,1", "EMPTY,ML_INVALID_RESULT,1", "SAVE,GENERATION_FAILED,1"})
    void failuresDoNotPersist(String fault, String code, int calls) throws Exception {
        String owner = register();
        String material = upload(owner, "TXT");
        JsonNode before = mode(fault);
        String id = start(owner, settings(material, 5, "MEDIUM", "SINGLE_CHOICE"), UUID.randomUUID().toString());
        JsonNode terminal = awaitTerminal(owner, id);
        assertEquals("FAILED", terminal.path("status").asText());
        assertEquals(code, safeCode(terminal.path("error").path("code")));
        assertFalse(terminal.hasNonNull("quizId"), "FAILED не должен указывать на тест");
        assertEmptyList(owner);
        JsonNode after = mode("SUCCESS");
        assertDelta(before, after, "jobs", 1);
        assertDelta(before, after, "calls", calls);
        assertDelta(before, after, "quizzes", 0);
        passed = true;
    }

    @Test
    @Tag("generation-live")
    @DisplayName("API-05: реальная ML-модель, генерация и сохранение")
    void liveModelPersistsQuiz() throws Exception {
        String owner = register();
        String material = upload(owner, "TXT");
        String job = start(owner, settings(material, 5, "MEDIUM", "SINGLE_CHOICE"), UUID.randomUUID().toString());
        verifySaved(owner, material, "TXT", 5, "MEDIUM", "SINGLE_CHOICE", awaitTerminal(owner, job));
        passed = true;
    }

    private void verifySaved(String owner, String material, String format, int count, String difficulty,
                             String type, JsonNode terminal) throws Exception {
        assertEquals("COMPLETED", terminal.path("status").asText(), "Генерация: " +
                safeCode(terminal.path("error").path("code")));
        String quiz = uuid(terminal.path("quizId"));
        JsonNode items = items(owner);
        assertEquals(1, items.size(), "Ожидался ровно один сохранённый тест");
        JsonNode item = items.get(0);
        assertEquals(quiz, uuid(item.path("id")));
        assertEquals(material, uuid(item.path("materialId")));
        assertEquals(format, item.path("format").asText());
        assertEquals(count, item.path("questionCount").asInt());
        assertEquals(type, item.path("questionType").asText());
        assertEquals(difficulty, item.path("difficulty").asText());
        JsonNode detail = body(expect("GET", QUIZZES + "/" + quiz, 200, request(base, owner)));
        assertTrue(item.equals(detail.path("quiz")), "Список и детали теста расходятся");
        JsonNode questions = detail.path("questions");
        assertTrue(questions.isArray());
        assertEquals(count, questions.size());
        Set<String> ids = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (JsonNode question : questions) {
            assertEquals(type, question.path("type").asText());
            assertTrue(ids.add(uuid(question.path("id"))), "Дубликат ID вопроса");
            String text = question.path("text").asText().strip();
            assertTrue(!text.isEmpty() && texts.add(text), "Пустой или повторный вопрос");
            assertTrue(question.path("options").isArray());
            assertEquals(type.equals("TRUE_FALSE") ? 2 : 4, question.path("options").size());
        }
    }

    private JsonNode awaitTerminal(String token, String id) throws Exception {
        Instant deadline = Instant.now().plusSeconds(liveRun ? 450 : 90);
        while (Instant.now().isBefore(deadline)) {
            JsonNode job = status(token, id);
            if (Set.of("COMPLETED", "FAILED").contains(job.path("status").asText())) return job;
            assertTrue(PENDING.contains(job.path("status").asText()), "Неизвестный статус задачи");
            Thread.sleep(1_000);
        }
        return fail("Истёк тайм-аут ожидания генерации");
    }

    private JsonNode status(String token, String id) throws Exception {
        JsonNode value = body(expect("GET", JOBS + id, 200, request(base, token)));
        assertEquals(id, uuid(value.path("id")));
        Map<String, Object> row = requests.get(requests.size() - 1);
        row.put("jobStatus", safeCode(value.path("status")));
        if (value.hasNonNull("error")) row.put("jobError", safeCode(value.path("error").path("code")));
        return value;
    }

    private String start(String token, Map<String, Object> data, String key) throws Exception {
        Response response = expect("POST", QUIZZES, 202, startRequest(token, data, key));
        String id = uuid(body(response).path("id"));
        assertEquals(JOBS + id, response.header("Location"), "Location должен указывать на задачу");
        return id;
    }

    private void reject(String token, Map<String, Object> data) throws Exception {
        expect("POST", QUIZZES, 400, startRequest(token, data, UUID.randomUUID().toString()));
    }

    private RequestSpecification startRequest(String token, Map<String, Object> data, String key) throws Exception {
        var req = json(token, data);
        if (key != null) req.header("Idempotency-Key", key);
        return req;
    }

    private String register() throws Exception {
        String name = "qa67_" + UUID.randomUUID().toString().replace("-", "");
        String token = body(expect("POST", "/api/auth/register", 200, json(null, Map.of(
                "username", name, "email", name + "@example.org", "password", UUID.randomUUID() + "Qa1!"))))
                .path("token").asText();
        assertFalse(token.isBlank(), "Регистрация должна вернуть токен");
        return token;
    }

    private String upload(String token, String format) throws Exception {
        try (var fixtures = new ApiGenerationFiles()) {
            Path file = fixtures.file(format);
            var req = request(base, token).multiPart("metadata", "metadata.json",
                    JSON.writeValueAsBytes(Map.of("name", "Лекция " + format, "subjectName", "Тестирование")),
                    "application/json").multiPart("file", file.toFile());
            return uuid(body(expect("POST", "/api/materials", 201, req)).path("material").path("id"));
        }
    }

    private JsonNode mode(String value) throws Exception {
        return body(expect("GET", "/control?mode=" + value, 200, request(control, null)));
    }

    private JsonNode items(String token) throws Exception {
        JsonNode items = body(expect("GET", QUIZZES, 200, request(base, token))).path("items");
        assertTrue(items.isArray(), "Список должен содержать массив items");
        return items;
    }

    private void assertEmptyList(String token) throws Exception {
        assertEquals(0, items(token).size(), "Сохранённого теста быть не должно");
    }

    private static void assertOptions(JsonNode options, Set<String> expected) {
        assertTrue(options.isArray());
        assertEquals(expected.size(), options.size());
        Set<String> values = new HashSet<>();
        options.forEach(option -> values.add(option.path("value").asText()));
        assertEquals(expected, values);
    }

    private static void assertDefaultAvailable(JsonNode options, JsonNode expected) {
        for (JsonNode option : options) {
            if (option.path("value").equals(expected) && option.path("available").asBoolean()) return;
        }
        fail("Значение по умолчанию должно быть доступно");
    }

    private static void assertDelta(JsonNode before, JsonNode after, String key, int expected) {
        assertTrue(before.path(key).isIntegralNumber() && after.path(key).isIntegralNumber());
        assertEquals(expected, after.path(key).asLong() - before.path(key).asLong(), key);
    }

    private static Map<String, Object> settings(String material, int count, String difficulty, String type) {
        return Map.of("materialId", material, "name", "Тест API 67", "questionCount", count,
                "difficulty", difficulty, "questionType", type);
    }

    private static Map<String, Object> changed(Map<String, Object> source, String field, Object value) {
        var copy = new LinkedHashMap<>(source);
        copy.put(field, value);
        return copy;
    }

    private RequestSpecification json(String token, Object value) throws Exception {
        return request(base, token).contentType("application/json; charset=UTF-8").body(JSON.writeValueAsBytes(value));
    }

    private static RequestSpecification request(String base, String token) {
        var req = RestAssured.given().baseUri(base).redirects().follow(false)
                .config(RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 30_000).setParam("http.socket.timeout", 30_000)));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return req;
    }

    private Response expect(String method, String path, int expected, RequestSpecification req) {
        var row = new LinkedHashMap<String, Object>();
        row.put("method", method);
        row.put("path", path);
        row.put("expected", expected);
        requests.add(row);
        Response response;
        try { response = req.request(method, path); }
        catch (Exception ex) {
            row.put("actual", "HTTP-ответ не получен");
            throw new AssertionError(method + " " + path + ": " + ex.getClass().getSimpleName());
        }
        row.put("actual", response.statusCode());
        assertEquals(expected, response.statusCode(), method + " " + path);
        return response;
    }

    private static JsonNode body(Response response) {
        try { return JSON.readTree(response.asByteArray()); }
        catch (Exception ex) { throw new AssertionError("Backend вернул некорректный JSON"); }
    }

    private static String uuid(JsonNode value) {
        String id = value.asText();
        assertTrue(id.matches("(?i)[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"), "Ожидался UUID");
        return id;
    }

    private static String safeCode(JsonNode value) {
        String code = value.asText();
        return code.matches("[A-Z_]{1,64}") ? code : "UNKNOWN";
    }

    private static String localUrl(String env, String fallback) {
        URI url = URI.create(System.getenv().getOrDefault(env, fallback));
        assertTrue("http".equals(url.getScheme()) && url.getHost() != null &&
                Set.of("localhost", "127.0.0.1", "[::1]").contains(url.getHost()) &&
                url.getUserInfo() == null && url.getQuery() == null && url.getFragment() == null &&
                Set.of("", "/").contains(url.getPath()), "Нужен локальный HTTP-адрес для " + env);
        return url.toString();
    }
}
