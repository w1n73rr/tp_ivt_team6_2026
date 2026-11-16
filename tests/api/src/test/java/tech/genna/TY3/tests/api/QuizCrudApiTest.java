package tech.genna.TY3.tests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("quiz-crud")
class QuizCrudApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String QUIZZES = "/api/quizzes";
    private static final Set<String> SUMMARY = Set.of("id", "name", "materialId", "materialName",
            "format", "subjectId", "subjectName", "kind", "difficulty", "questionType", "questionCount", "createdAt");

    private final List<Map<String, Object>> exchanges = new ArrayList<>();
    private String base;
    private String revision;
    private String mlMode;
    private String step = "Подготовка";
    private boolean passed;

    @BeforeEach
    void requireDisposableStand() {
        assertEquals("true", System.getenv("API_TEST_DISPOSABLE"), "Используйте отдельный тестовый стенд");
        URI url = URI.create(System.getenv().getOrDefault("API_BASE_URL", "http://localhost:18083"));
        assertTrue("http".equals(url.getScheme()) && url.getHost() != null &&
                Set.of("localhost", "127.0.0.1", "[::1]").contains(url.getHost()) && url.getUserInfo() == null &&
                url.getQuery() == null && url.getFragment() == null &&
                Set.of("", "/").contains(url.getPath()), "Нужен локальный HTTP-адрес стенда");
        revision = System.getenv().getOrDefault("API_TEST_VERSION", "");
        assertTrue(revision.matches("[a-fA-F0-9]{7,40}"), "Укажите SHA Backend в API_TEST_VERSION");
        mlMode = System.getenv().getOrDefault("API_TEST_ML_MODE", "controlled");
        assertTrue(Set.of("controlled", "live").contains(mlMode), "Ожидается controlled или live");
        base = url.toString();
    }

    @AfterEach
    void writeAudit() throws Exception {
        if (base == null) return;
        Path file = Path.of("build/reports/quiz-crud-http.json");
        Files.createDirectories(file.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), Map.of(
                "task", "tpivt6_26-79", "revision", revision, "baseUrl", base, "mlMode", mlMode,
                "finishedAt", Instant.now().toString(), "result", passed ? "PASSED" : "FAILED",
                "requests", exchanges));
    }

    @Test
    @DisplayName("TY5: запись генерации, чтение, права, переименование и удаление тестов")
    void verifiesSavedQuizLifecycle() throws Exception {
        String owner = register();
        String other = register();
        same(JSON.createArrayNode(), list(owner), "У нового владельца пустой список");
        String material = upload(owner);

        step = "Генерация и сохранение";
        String first = generate(owner, material, "Первый тест QA");
        String second = generate(owner, material, "Второй тест QA");
        assertNotEquals(first, second, "Генерации должны сохранить разные тесты");
        JsonNode original = detail(owner, first);
        JsonNode neighbour = detail(owner, second);
        JsonNode summary = original.path("quiz");
        same(JSON.valueToTree(Map.of("id", first, "materialId", material, "materialName", "Лекция QA 79",
                        "name", "Первый тест QA", "questionCount", 5, "questionType", "SINGLE_CHOICE")),
                selectedFields(summary, "id", "materialId", "materialName", "name", "questionCount", "questionType"),
                "Сохранённый тест соответствует запуску");
        same(JSON.createArrayNode().add(neighbour.path("quiz")).add(summary), list(owner), "Два теста в порядке создания");
        same(JSON.createArrayNode(), list(other), "Список второго пользователя не раскрывает чужие тесты");

        step = "Без авторизации, чужой и отсутствующий ID";
        String path = QUIZZES + "/" + first;
        String missing = QUIZZES + "/" + UUID.randomUUID();
        expect("GET", QUIZZES, 401, request(null), "AUTHENTICATION_REQUIRED");
        for (String method : List.of("GET", "PATCH", "DELETE")) {
            expect(method, path, 401, accessRequest(method, null), "AUTHENTICATION_REQUIRED");
            expect(method, path, 404, accessRequest(method, other), "QUIZ_NOT_FOUND");
            expect(method, missing, 404, accessRequest(method, owner), "QUIZ_NOT_FOUND");
        }
        same(original, detail(owner, first), "Запрещённые запросы не изменяют тест владельца");

        step = "Валидация названия";
        renameError(path, owner, Map.of("name", ""), 400, "VALIDATION_ERROR");
        for (String name : List.of("   ", "Я".repeat(101))) {
            renameError(path, owner, Map.of("name", name), 400, "INVALID_QUIZ_NAME");
        }
        renameError(path, owner, Map.of("name", "Новое имя", "questionCount", 10), 400, "VALIDATION_ERROR");
        renameError(path, owner, Map.of("name", neighbour.path("quiz").path("name").asText()), 409,
                "QUIZ_NAME_ALREADY_EXISTS");
        same(original, detail(owner, first), "Отклонённые названия не меняют сохранённый тест");

        step = "Переименование и повторное чтение";
        ObjectNode renamed = summary.deepCopy();
        renamed.put("name", "Тест QA — изменён");
        same(renamed, body(expect("PATCH", path, 200, json(owner, Map.of("name", "  Тест QA — изменён  ")), null)),
                "PATCH меняет только имя и обрезает пробелы");
        ObjectNode updated = original.deepCopy();
        updated.set("quiz", renamed);
        same(updated, detail(owner, first), "Детали подтверждают имя и сохранение остальных полей");
        same(JSON.createArrayNode().add(neighbour.path("quiz")).add(renamed), list(owner), "Список подтверждает новое имя");

        step = "Удаление и повторное чтение";
        assertEquals(0, expect("DELETE", path, 204, request(owner), null).asByteArray().length,
                "Ответ DELETE 204 должен быть пустым");
        expect("GET", path, 404, request(owner), "QUIZ_NOT_FOUND");
        expect("DELETE", path, 404, request(owner), "QUIZ_NOT_FOUND");
        same(JSON.createArrayNode().add(neighbour.path("quiz")), list(owner), "В списке остался второй тест");
        same(neighbour, detail(owner, second), "Второй тест не изменился");
        expect("GET", "/api/materials/" + material, 200, request(owner), null);
        passed = true;
        System.out.println("CRUD API: " + exchanges.size() + " HTTP-запросов проверено");
    }

    private String register() throws Exception {
        String username = "qa79_" + UUID.randomUUID().toString().replace("-", "");
        JsonNode data = body(expect("POST", "/api/auth/register", 200, json(null, Map.of(
                "username", username, "email", username + "@example.org",
                "password", UUID.randomUUID() + "Qa1!")), null));
        String token = data.path("token").asText();
        assertFalse(token.isBlank(), "Регистрация должна вернуть токен");
        return token;
    }

    private String upload(String token) throws Exception {
        try (var resource = getClass().getResourceAsStream("/quiz-crud/lecture.txt")) {
            assertNotNull(resource, "Тестовая лекция отсутствует");
            var multipart = request(token)
                    .multiPart("metadata", "metadata.json", JSON.writeValueAsBytes(
                            Map.of("name", "Лекция QA 79", "subjectName", "Тестирование ПО")), "application/json")
                    .multiPart("file", "lecture.txt", resource.readAllBytes(), "text/plain");
            return uuid(body(expect("POST", "/api/materials", 201, multipart, null)).path("material").path("id"));
        }
    }

    private String generate(String token, String material, String name) throws Exception {
        var request = json(token, Map.of("materialId", material, "name", name, "questionCount", 5,
                "difficulty", "MEDIUM", "questionType", "SINGLE_CHOICE"))
                .header("Idempotency-Key", UUID.randomUUID().toString());
        Response accepted = expect("POST", QUIZZES, 202, request, null);
        String job = uuid(body(accepted).path("id"));
        String path = "/api/generation-jobs/" + job;
        assertEquals(path, accepted.header("Location"), "Location должен указывать на задачу");
        Instant deadline = Instant.now().plus(Duration.ofMinutes(8));
        while (Instant.now().isBefore(deadline)) {
            JsonNode status = body(expect("GET", path, 200, request(token), null));
            assertEquals(job, uuid(status.path("id")), "Неверный ID задачи");
            switch (status.path("status").asText()) {
                case "COMPLETED": return uuid(status.path("quizId"));
                case "FAILED": fail("Генерация завершилась ошибкой " + safeCode(status.path("error").path("code")));
                case "QUEUED", "RUNNING": Thread.sleep(1_000);
                    break;
                default: fail("Неизвестный статус генерации");
            }
        }
        return fail("Истёк тайм-аут генерации");
    }

    private JsonNode list(String token) throws Exception {
        Response response = expect("GET", QUIZZES, 200, request(token), null);
        assertTrue(response.header("Cache-Control").contains("no-store"), "Список не должен кешироваться");
        JsonNode value = body(response);
        fields(value, Set.of("items"), Set.of());
        assertTrue(value.path("items").isArray(), "items должен быть массивом");
        value.path("items").forEach(QuizCrudApiTest::publicSummary);
        return value.path("items");
    }

    private JsonNode detail(String token, String id) throws Exception {
        Response response = expect("GET", QUIZZES + "/" + id, 200, request(token), null);
        assertTrue(response.header("Cache-Control").contains("no-store"), "Детали не должны кешироваться");
        JsonNode value = body(response);
        fields(value, Set.of("quiz", "settings", "questions"), Set.of());
        publicSummary(value.path("quiz"));
        same(JSON.valueToTree(Map.of("questionCount", 5, "difficulty", "MEDIUM", "questionType", "SINGLE_CHOICE")),
                value.path("settings"), "Настройки должны совпадать с запуском");
        JsonNode questions = value.path("questions");
        assertTrue(questions.isArray() && questions.size() == 5, "Должны сохраниться пять вопросов");
        int position = 0;
        for (JsonNode question : questions) {
            fields(question, Set.of("id", "position", "type", "text", "options"), Set.of("topicId", "topicName"));
            uuid(question.path("id"));
            assertEquals(++position, question.path("position").asInt(), "Неверный порядок вопросов");
            assertTrue("SINGLE_CHOICE".equals(question.path("type").asText()) &&
                    question.path("text").isTextual() && !question.path("text").asText().isBlank(),
                    "Вопрос должен содержать тип и текст");
            JsonNode options = question.path("options");
            assertTrue(options.isArray() && options.size() == 4, "Ожидались четыре варианта ответа");
            for (JsonNode option : options) {
                fields(option, Set.of("id", "text"), Set.of());
                uuid(option.path("id"));
                assertTrue(option.path("text").isTextual() && !option.path("text").asText().isBlank(),
                        "Вариант должен содержать текст");
            }
        }
        return value;
    }

    private static void publicSummary(JsonNode summary) {
        fields(summary, SUMMARY, Set.of("topics"));
        SUMMARY.forEach(key -> assertTrue(summary.path(key).isTextual() || summary.path(key).isNumber(),
                "Публичное сводное поле должно быть скалярным: " + key));
        if (summary.has("topics")) {
            assertTrue(summary.path("topics").isArray(), "Темы должны быть массивом");
            for (JsonNode topic : summary.path("topics")) {
                fields(topic, Set.of("id", "name"), Set.of());
                uuid(topic.path("id"));
                assertTrue(topic.path("name").isTextual(), "Название темы должно быть строкой");
            }
        }
    }

    private static void fields(JsonNode node, Set<String> required, Set<String> optional) {
        assertTrue(node.isObject() && required.stream().allMatch(node::has), "Отсутствуют обязательные публичные поля");
        node.fieldNames().forEachRemaining(key -> assertTrue(required.contains(key) || optional.contains(key),
                "Ответ содержит непредусмотренное поле"));
    }

    private static ObjectNode selectedFields(JsonNode node, String... keys) {
        ObjectNode result = JSON.createObjectNode();
        for (String key : keys) result.set(key, node.path(key));
        return result;
    }

    private void renameError(String path, String token, Map<String, Object> data, int status, String code) throws Exception {
        expect("PATCH", path, status, json(token, data), code);
    }

    private RequestSpecification accessRequest(String method, String token) throws Exception {
        return method.equals("PATCH") ? json(token, Map.of("name", "Запрещённое изменение")) : request(token);
    }

    private RequestSpecification request(String token) {
        var req = RestAssured.given().baseUri(base).redirects().follow(false)
                .config(RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 30_000).setParam("http.socket.timeout", 30_000)));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return req;
    }

    private RequestSpecification json(String token, Object value) throws Exception {
        return request(token).contentType("application/json; charset=UTF-8").body(JSON.writeValueAsBytes(value));
    }

    private Response expect(String method, String path, int status, RequestSpecification req, String code) throws Exception {
        var entry = new java.util.LinkedHashMap<String, Object>();
        entry.put("step", step);
        entry.put("method", method);
        entry.put("path", path);
        entry.put("expected", status);
        exchanges.add(entry);
        Response response;
        try { response = req.request(method, path); }
        catch (Exception ex) {
            entry.put("actual", "HTTP-ответ не получен");
            throw new AssertionError(method + " " + path + ": " + ex.getClass().getSimpleName());
        }
        entry.put("actual", response.statusCode());
        if (code != null) {
            entry.put("expectedCode", code);
            entry.put("actualCode", safeCode(body(response).path("code")));
        }
        assertEquals(status, response.statusCode(), method + " " + path);
        if (code != null) assertEquals(code, entry.get("actualCode"), "Код ошибки " + method + " " + path);
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

    private static void same(JsonNode expected, JsonNode actual, String message) {
        assertTrue(expected.equals(actual), message);
    }
}
