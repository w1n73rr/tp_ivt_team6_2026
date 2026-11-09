package tech.genna.TY3.tests.ui;

import com.codeborne.selenide.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.Keys;
import tech.genna.TY3.tests.ui.pages.*;
import tech.genna.TY3.tests.ui.support.BrowserSettings;
import tech.genna.TY3.tests.ui.support.GenerationTraffic;
import tech.genna.TY3.tests.ui.support.GenerationControl;
import tech.genna.TY3.tests.ui.support.GenerationFiles;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.remote.http.*;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.junit.jupiter.api.Assertions.*;

public class QuizGenerationUiTest {
    private final MaterialsPage materials = new MaterialsPage();
    private final GenerationPage generation = new GenerationPage();
    private final TestsPage tests = new TestsPage();
    private Path lecture;
    private boolean controlled;
    private final String materialName = "Лекция QA " + UUID.randomUUID().toString().substring(0, 8);
    private final String quizName = "Тест QA " + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void prepareThroughBrowser(TestInfo info) throws Exception {
        BrowserSettings.configure();
        Configuration.pageLoadStrategy = "eager";
        if (info.getTags().contains("controlled")) {
            controlled = true;
            Configuration.baseUrl = System.getProperty("ui.controlledBaseUrl", "http://localhost:13002");
            GenerationControl.mode("SUCCESS");
        }
        closeWebDriver();
        if (controlled) GenerationControl.mode("SUCCESS");
        lecture = Files.createTempFile("qa-generation-", ".txt");
        try (var input = getClass().getResourceAsStream("/generation/lecture.txt")) {
            if (input == null) throw new IllegalStateException("Missing lecture fixture");
            Files.write(lecture, input.readAllBytes());
        }
        String username = "qa_gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new RegisterPage().openPage().register(username, username + "@example.com", "QaGen2026!Local");
        materials.openPage().openUploadForm().chooseFile(lecture.toFile()).name(materialName)
                .createSubject("Тестирование ПО").submit().shouldShowSuccess().backToMaterials();
    }

    @AfterEach
    void closeBrowser() throws Exception {
        // Isolated account per test; records stay for inspection, active jobs are not deleted.
        if (controlled) GenerationControl.mode("SUCCESS");
        closeWebDriver();
        if (lecture != null) Files.deleteIfExists(lecture);
    }

    private void openSettings() {
        materials.material(materialName).generateButton.click();
        generation.shouldBeOpen();
    }

    @Test @DisplayName("TC-GEN-01: настройка выбранного материала и возврат")
    void openSelectedMaterial() throws Exception {
        openSettings();
        generation.materialCard.shouldHave(text(materialName), text("TXT"), text(Files.size(lecture) < 1024 ? "Б" : "КБ"));
        generation.materialDate.shouldNot(exist);
        generation.forbiddenControls.shouldHave(size(0));
        generation.materialCard.shouldNotHave(text("Страниц"), text("Обработка ИИ"));
        generation.backLink.click();
        materials.shouldBeOpen().shouldContain(materialName);
    }

    @ParameterizedTest(name = "Один выбранный тип: {0}")
    @ValueSource(strings = {"Один ответ", "Несколько ответов", "Верно/неверно"})
    @DisplayName("TC-GEN-02/03: количество, сложности, ровно один тип")
    void selectSettings(String type) {
        openSettings();
        generation.tenQuestions.shouldBe(selected);
        generation.medium.shouldBe(selected);
        generation.singleChoice.shouldBe(selected);
        generation.countOptions.shouldHave(size(2));
        generation.fiveQuestions.click();
        generation.summary.shouldHave(text("5 вопросов"));
        generation.tenQuestions.click();
        generation.summary.shouldHave(text("10 вопросов"));
        generation.fiveQuestions.click();
        generation.easy.click();
        generation.summary.shouldHave(text("лёгкая сложность"));
        generation.hard.click();
        generation.summary.shouldHave(text("сложная сложность"));
        generation.medium.click();
        generation.summary.shouldHave(text("средняя сложность"));
        switch (type) {
            case "Один ответ" -> generation.singleChoice.click();
            case "Несколько ответов" -> generation.multipleChoice.click();
            default -> generation.trueFalse.click();
        }
        generation.selectedTypes.shouldHave(size(1));
        generation.selectedTypes.first().parent().shouldHave(text(type));
        generation.selectedTypes.first().click();
        generation.selectedTypes.shouldHave(size(1));
        generation.summary.shouldHave(text("Тест будет составлен по выбранной лекции"));
        generation.createButton.shouldBe(enabled);
    }

    @Test @DisplayName("Валидация: пустое название не запускает генерацию")
    void rejectEmptyName() {
        openSettings();
        generation.nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        generation.nameInput.shouldHave(domProperty("value", ""));
        generation.createButton.click();
        generation.validationError.shouldHave(text("Введите название теста"));
        generation.title.shouldHave(exactText("Настройка генерации теста"));
        generation.waitingPanel.shouldNot(exist);
    }

    @Test @Tag("live-ml")
    @DisplayName("TC-GEN-05: настоящий запуск из UI, COMPLETED, один тест после обновления")
    @SuppressWarnings("unchecked")
    void generateAndPersistRealQuiz() {
        openSettings();
        assertFalse(generation.demoNotice.exists(),
                "BLOCKED: frontend uses DemoGenerationSource; real UI → BE → ML is not connected");
        generation.nameInput.setValue(quizName);
        generation.fiveQuestions.click();
        generation.medium.click();
        generation.singleChoice.click();
        String materialId = getWebDriver().getCurrentUrl().split("/materials/")[1].split("/")[0];
        try (GenerationTraffic traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click();
            new WebDriverWait(getWebDriver(), Duration.ofSeconds(20)).until(d -> traffic.accepted != null);
            assertEquals(5, ((Number) traffic.submitted.get("questionCount")).intValue());
            assertEquals("MEDIUM", traffic.submitted.get("difficulty"));
            assertEquals("SINGLE_CHOICE", traffic.submitted.get("questionType"));
            assertEquals(materialId, traffic.submitted.get("materialId"));
            assertEquals(quizName, traffic.submitted.get("name"));
            String jobId = (String) traffic.accepted.get("id");
            Map<String, Object> state = new WebDriverWait(getWebDriver(), Duration.ofSeconds(450))
                    .pollingEvery(Duration.ofSeconds(2)).until(d -> {
                        var snapshot = traffic.get("/api/generation-jobs/" + jobId);
                        return List.of("COMPLETED", "FAILED").contains(snapshot.get("status")) ? snapshot : null;
                    });
            assertEquals("COMPLETED", state.get("status"), "Generation failed: " + state.get("error"));
            assertNotNull(state.get("quizId"));
            assertEquals(jobId, traffic.replayLastSubmission().get("id"));
            List<Map<String, Object>> saved = (List<Map<String, Object>>) traffic.get("/api/quizzes").get("items");
            assertEquals(1, saved.size());
            assertEquals(state.get("quizId"), saved.getFirst().get("id"));
            assertEquals(5, ((Number) saved.getFirst().get("questionCount")).intValue());
            assertEquals("SINGLE_CHOICE", saved.getFirst().get("questionType"));
            generation.readyPanel.shouldBe(visible, Duration.ofSeconds(30)).shouldHave(text(quizName));
            generation.readyLink.click();
            tests.title.shouldHave(exactText("Тесты"));
            tests.demoNotice.shouldNot(exist);
            tests.searchInput.setValue(quizName);
            tests.rows.shouldHave(size(1));
            tests.rows.first().shouldHave(text(quizName), text(materialName));
            refresh();
            tests.searchInput.shouldBe(visible).setValue(quizName);
            tests.rows.shouldHave(size(1));
            tests.rows.first().shouldHave(text(quizName), text(materialName));
        }
    }

    private String materialId() { return getWebDriver().getCurrentUrl().split("/materials/")[1].split("/")[0]; }
    private Map<String, Object> parameters() {
        return new LinkedHashMap<>(Map.of("materialId", materialId(), "name", quizName,
                "questionCount", 5, "difficulty", "MEDIUM", "questionType", "SINGLE_CHOICE"));
    }
    private void settings(int count, String difficulty, String type) {
        generation.nameInput.setValue(quizName);
        if (count == 5) generation.fiveQuestions.click(); else generation.tenQuestions.click();
        switch (difficulty) { case "EASY" -> generation.easy.click(); case "HARD" -> generation.hard.click(); default -> generation.medium.click(); }
        switch (type) { case "SINGLE_CHOICE" -> generation.singleChoice.click(); case "MULTIPLE_CHOICE" -> generation.multipleChoice.click(); default -> generation.trueFalse.click(); }
    }
    private void accepted(GenerationTraffic traffic) {
        new WebDriverWait(getWebDriver(), Duration.ofSeconds(15)).until(d -> traffic.accepted != null);
    }
    private Map<String, Object> terminal(GenerationTraffic traffic) {
        accepted(traffic);
        return new WebDriverWait(getWebDriver(), Duration.ofSeconds(40)).pollingEvery(Duration.ofMillis(500)).until(d -> {
            var result = traffic.get("/api/generation-jobs/" + traffic.accepted.get("id"));
            return List.of("COMPLETED", "FAILED").contains(result.get("status")) ? result : null;
        });
    }
    private void uploadFormat(String format) throws Exception {
        materials.deleteIfPresent(materialName);
        try (var files = new GenerationFiles()) {
            materials.openUploadForm().chooseFile(files.file(format).toFile()).name(materialName)
                    .subject("Тестирование ПО").submit().shouldShowSuccess().backToMaterials();
        }
    }

    @ParameterizedTest(name = "{0}: {1}/{2}/{3}") @Tag("controlled")
    @CsvSource({"PDF,5,EASY,SINGLE_CHOICE", "DOCX,10,MEDIUM,MULTIPLE_CHOICE", "TXT,5,HARD,TRUE_FALSE"})
    @DisplayName("TC-GEN-04/05: форматы, реальные сохранение/refresh и один тип; мок ML")
    @SuppressWarnings("unchecked")
    void formatsWithControlledMl(String format, int count, String difficulty, String type) throws Exception {
        uploadFormat(format);
        openSettings(); settings(count, difficulty, type);
        GenerationControl.mode("WAIT");
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); accepted(traffic);
            String id = materialId();
            assertEquals(id, traffic.submitted.get("materialId"));
            assertEquals(count, ((Number) traffic.submitted.get("questionCount")).intValue());
            assertEquals(difficulty, traffic.submitted.get("difficulty"));
            assertEquals(type, traffic.submitted.get("questionType"));
            generation.waitingPanel.shouldBe(visible);
            generation.waitingPanel.shouldHave(text(count + " вопросов"));
            generation.waitingPanel.shouldHave(text(switch (type) { case "SINGLE_CHOICE" -> "Один ответ"; case "MULTIPLE_CHOICE" -> "Несколько ответов"; default -> "Верно/неверно"; }));
            generation.title.shouldHave(exactText("Создаём тест"));
            new WebDriverWait(getWebDriver(), Duration.ofSeconds(6)).until(d -> traffic.statusRequests.size() >= 2);
            // Observe at least two real status snapshots before releasing controlled ML.
            String job = traffic.accepted.get("id").toString();
            assertTrue(traffic.statusRequests.stream().allMatch(path -> path.endsWith(job)));
            assertTrue(List.of("QUEUED", "RUNNING").contains(traffic.get("/api/generation-jobs/" + job).get("status")));
            assertTrue(List.of("QUEUED", "RUNNING").contains(traffic.get("/api/generation-jobs/" + job).get("status")));
            GenerationControl.mode("SUCCESS");
            var state = terminal(traffic);
            assertEquals("COMPLETED", state.get("status")); assertNotNull(state.get("quizId"));
            assertEquals(1, traffic.submissions.size(), "One browser action must not create extra requests");
            assertEquals(job, traffic.replayLastSubmission().get("id"));
            var saved = GenerationControl.result(job);
            var questions = (List<Map<String, Object>>) saved.get("questions");
            assertEquals(count, questions.size());
            assertEquals(count, new HashSet<>(questions.stream().map(q -> q.get("text")).toList()).size());
            assertEquals(count, new HashSet<>(questions.stream().map(q -> q.get("id")).toList()).size());
            for (var q : questions) {
                assertEquals(type.toLowerCase(java.util.Locale.ROOT), q.get("type"));
                var options = (List<Map<String, Object>>) q.get("options");
                var correct = (List<String>) q.get("correctOptionIds");
                assertEquals(type.equals("TRUE_FALSE") ? 2 : 4, options.size());
                assertTrue(type.equals("MULTIPLE_CHOICE") ? correct.size() >= 2 && correct.size() <= 3 : correct.size() == 1);
                assertTrue(options.stream().map(o -> o.get("id")).toList().containsAll(correct));
            }
            generation.readyPanel.shouldBe(visible).shouldHave(text(quizName));
            generation.readyLink.click(); tests.title.shouldHave(exactText("Тесты"));
            tests.searchInput.setValue(quizName); tests.rows.shouldHave(size(1));
            tests.rows.first().shouldHave(text(materialName), text(format), text(count + ""));
            refresh(); tests.searchInput.shouldBe(visible).setValue(quizName);
            tests.rows.shouldHave(size(1)); tests.rows.first().shouldHave(text(quizName));
            var list = (List<Map<String, Object>>) traffic.get("/api/quizzes").get("items"); assertEquals(1, list.size());
            var summary = list.getFirst();
            assertEquals(state.get("quizId"), summary.get("id"));
            assertEquals(id, summary.get("materialId"));
            assertEquals(count, ((Number) summary.get("questionCount")).intValue());
            assertEquals(type, summary.get("questionType"));
            assertEquals(difficulty, summary.get("difficulty"));
            assertEquals(format, summary.get("format"));
        }
    }

    @ParameterizedTest @Tag("controlled")
    @ValueSource(strings = {"EXTRACT", "ML_UNAVAILABLE", "INVALID", "EMPTY", "SAVE"})
    @DisplayName("TC-GEN-07: реальные FAILED и отсутствие сохранения при управляемом сбое")
    void terminalFailures(String mode) {
        openSettings(); settings(5, "MEDIUM", "SINGLE_CHOICE");
        GenerationControl.mode(mode);
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); var state = terminal(traffic);
            assertEquals("FAILED", state.get("status")); assertNull(state.get("quizId"));
            generation.failurePanel.shouldBe(visible).shouldHave(text("Не удалось создать тест"), text("параметры сохранены"));
            generation.failurePanel.shouldNotHave(text("Exception"), text("Injected"), text("sk-or-"));
            generation.retryButton.shouldBe(enabled); generation.settingsButton.shouldBe(enabled);
            assertTrue(((List<?>) traffic.get("/api/quizzes").get("items")).isEmpty());
        }
    }

    @Test @Tag("controlled") @DisplayName("TC-GEN-08: повтор ошибки — новая задача, прежние настройки, один тест")
    void retryFailedJob() {
        openSettings(); settings(5, "HARD", "SINGLE_CHOICE"); GenerationControl.mode("ML_UNAVAILABLE");
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); assertEquals("FAILED", terminal(traffic).get("status"));
            generation.failurePanel.shouldBe(visible);
            GenerationControl.mode("SUCCESS"); generation.retryButton.click();
            new WebDriverWait(getWebDriver(), Duration.ofSeconds(15)).until(d -> traffic.jobs.size() == 2);
            assertNotEquals(traffic.jobs.get(0).get("id"), traffic.jobs.get(1).get("id"));
            assertNotEquals(traffic.keys.get(0), traffic.keys.get(1));
            assertEquals(traffic.submissions.get(0), traffic.submissions.get(1));
            assertEquals("COMPLETED", terminal(traffic).get("status"));
            generation.readyPanel.shouldBe(visible); assertEquals(1, ((List<?>) traffic.get("/api/quizzes").get("items")).size());
        }
    }

    @Test @Tag("controlled") @DisplayName("TC-GEN-09: возврат из ошибки сохраняет материал и все настройки")
    void returnFromFailure() {
        openSettings(); settings(5, "EASY", "TRUE_FALSE"); GenerationControl.mode("INVALID");
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); assertEquals("FAILED", terminal(traffic).get("status"));
            generation.settingsButton.shouldBe(visible, Duration.ofSeconds(3)).click(); generation.shouldBeOpen();
            generation.materialCard.shouldHave(text(materialName)); generation.nameInput.shouldHave(value(quizName));
            generation.fiveQuestions.shouldBe(selected); generation.easy.shouldBe(selected); generation.trueFalse.shouldBe(selected);
        }
    }

    @Test @Tag("controlled") @DisplayName("TC-GEN-09: параметры заблокированы во время ожидания, задача завершается")
    void returnFromWaiting() {
        openSettings(); settings(5, "EASY", "TRUE_FALSE"); GenerationControl.mode("WAIT");
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); accepted(traffic); generation.waitingPanel.shouldBe(visible);
            generation.settingsButton.shouldNot(exist);
            generation.nameInput.shouldNot(exist);
            generation.createButton.shouldNot(exist);
            GenerationControl.mode("SUCCESS"); assertEquals("COMPLETED", terminal(traffic).get("status"));
            generation.readyPanel.shouldBe(visible).shouldHave(text(quizName));
        }
    }

    @Test @Tag("controlled") @DisplayName("TC-GEN-09: завершение покинутой задачи не перенаправляет со списка")
    void completedJobDoesNotRedirectOtherPage() {
        openSettings(); GenerationControl.mode("WAIT");
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); accepted(traffic); generation.waitingPanel.shouldBe(visible);
            generation.materialsBreadcrumb.click(); materials.shouldBeOpen();
            GenerationControl.mode("SUCCESS"); assertEquals("COMPLETED", terminal(traffic).get("status"));
            materials.shouldBeOpen();
        }
    }

    static Stream<Arguments> invalidGenerationParameters() {
        var data = new ArrayList<Arguments>();
        for (int value : new int[]{0, 6, 11}) data.add(Arguments.of("questionCount=" + value, "questionCount", value, false));
        for (String field : List.of("materialId", "questionCount", "difficulty", "questionType")) data.add(Arguments.of("missing " + field, field, "", true));
        data.add(Arguments.of("unknown difficulty", "difficulty", "UNKNOWN", false));
        data.add(Arguments.of("unknown type", "questionType", "UNKNOWN", false));
        data.add(Arguments.of("empty type", "questionType", "", false));
        data.add(Arguments.of("two types", "questionType", List.of("SINGLE_CHOICE", "TRUE_FALSE"), false));
        data.add(Arguments.of("empty type array", "questionType", List.of(), false));
        return data.stream();
    }
    @ParameterizedTest(name = "{0}") @MethodSource("invalidGenerationParameters") @Tag("controlled")
    @DisplayName("TC-GEN-10: backend отклоняет параметры до вызова ML и создания задачи")
    void rejectInvalidParameters(String variant, String field, Object value, boolean remove) {
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            openSettings(); var params = parameters(); if (remove) params.remove(field); else params.put(field, value);
            var before = GenerationControl.stats();
            assertEquals(400, traffic.send("POST", "/api/quizzes", params, true), variant);
            assertEquals(before, GenerationControl.stats(), "No ML calls, jobs or quizzes for invalid requests");
            assertTrue(((List<?>) traffic.get("/api/quizzes").get("items")).isEmpty());
        }
    }

    @Test @Tag("controlled") @DisplayName("TC-GEN-11: без JWT, отсутствующий и чужой материал; ML не вызван")
    void denyUnauthorizedAndForeignMaterial() {
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            openSettings(); var own = parameters(); var before = GenerationControl.stats();
            assertEquals(401, traffic.send("POST", "/api/quizzes", own, false));
            var missing = new LinkedHashMap<>(own); missing.put("materialId", UUID.randomUUID().toString());
            assertEquals(404, traffic.send("POST", "/api/quizzes", missing, true));
            // CDP must not reconstruct multipart file uploads for the second account.
            traffic.stopObserving();
            closeWebDriver();
            String other = "qa_gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            new RegisterPage().openPage().register(other, other + "@example.com", "QaGen2026!Local");
            materials.openPage().openUploadForm().chooseFile(lecture.toFile()).name("Чужой материал")
                    .createSubject("Физика").submit().shouldShowSuccess().backToMaterials();
            materials.material("Чужой материал").generateButton.click(); generation.shouldBeOpen();
            var foreign = new LinkedHashMap<>(own); foreign.put("materialId", materialId());
            assertEquals(404, traffic.send("POST", "/api/quizzes", foreign, true));
            assertEquals(before, GenerationControl.stats());
            assertTrue(((List<?>) traffic.get("/api/quizzes").get("items")).isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"HTTP_500", "NETWORK"}) @Tag("controlled")
    @DisplayName("TC-GEN-12: ошибка запуска на форме, параметры сохранены")
    void startRequestFailure(String failure) {
        openSettings(); settings(5, "HARD", "SINGLE_CHOICE");
        var before = GenerationControl.stats();
        if (failure.equals("NETWORK")) {
            offline(true);
            try { generation.createButton.click(); assertLaunchErrorKeepsSettings(); }
            finally { offline(false); }
            assertEquals(before, GenerationControl.stats());
            return;
        }
        var attempts = new AtomicInteger();
        try (var interceptor = failStart(failure, attempts)) {
            generation.createButton.click();
            new WebDriverWait(getWebDriver(), Duration.ofSeconds(10)).until(d -> attempts.get() > 0);
            assertLaunchErrorKeepsSettings();
            assertEquals(before, GenerationControl.stats());
        }
    }
    private void assertLaunchErrorKeepsSettings() {
        generation.shouldBeOpen();
        generation.validationError.shouldBe(visible).shouldHave(text("Не удалось запустить генерацию"), text("Параметры сохранены"));
        generation.failurePanel.shouldNot(exist);
        generation.waitingPanel.shouldNot(exist);
        generation.nameInput.shouldHave(value(quizName)); generation.fiveQuestions.shouldBe(selected);
        generation.hard.shouldBe(selected); generation.singleChoice.shouldBe(selected);
    }
    private void offline(boolean value) {
        ((org.openqa.selenium.chromium.ChromiumDriver) getWebDriver()).executeCdpCommand("Network.enable", Map.of());
        ((org.openqa.selenium.chromium.ChromiumDriver) getWebDriver()).executeCdpCommand("Network.emulateNetworkConditions",
                Map.of("offline", value, "latency", 0, "downloadThroughput", -1, "uploadThroughput", -1));
    }
    @Test @Tag("controlled") @DisplayName("TC-GEN-08: ошибка принятия повтора сохраняет настройки на форме")
    void retryRequestFailureKeepsError() {
        openSettings(); settings(5, "HARD", "SINGLE_CHOICE"); GenerationControl.mode("INVALID");
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            generation.createButton.click(); assertEquals("FAILED", terminal(traffic).get("status"));
        }
        generation.failurePanel.shouldBe(visible);
        var before = GenerationControl.stats();
        try (var interceptor = failStart("HTTP_500", new AtomicInteger())) {
            generation.retryButton.click();
            generation.validationError.shouldBe(visible).shouldHave(text("Не удалось запустить генерацию"));
            generation.shouldBeOpen();
            generation.nameInput.shouldHave(value(quizName)); generation.hard.shouldBe(selected);
            generation.fiveQuestions.shouldBe(selected); generation.singleChoice.shouldBe(selected);
            assertEquals(before, GenerationControl.stats());
        }
    }
    private NetworkInterceptor failStart(String failure, AtomicInteger attempts) {
        return new NetworkInterceptor(getWebDriver(), (Filter) next -> request -> {
            if (request.getMethod() == HttpMethod.POST && java.net.URI.create(request.getUri()).getPath().equals("/api/quizzes")) {
                attempts.incrementAndGet();
                return new HttpResponse().setStatus(500).addHeader("Content-Type", "application/json").setContent(Contents.utf8String("{\"code\":\"GENERATION_FAILED\"}"));
            }
            return next.execute(request);
        });
    }
    @Test @Tag("controlled") @DisplayName("TC-GEN-02: defaults формы совпадают с серверным справочником")
    @SuppressWarnings("unchecked")
    void defaultsMatchServerCatalog() {
        try (var traffic = new GenerationTraffic(Configuration.baseUrl)) {
            openSettings();
            var catalog = traffic.get("/api/generation/parameters");
            assertEquals("SINGLE", catalog.get("questionTypeSelection"));
            var defaults = (Map<String, Object>) catalog.get("defaults");
            (((Number) defaults.get("questionCount")).intValue() == 5 ? generation.fiveQuestions : generation.tenQuestions).shouldBe(selected);
            (switch (defaults.get("difficulty").toString()) { case "EASY" -> generation.easy; case "HARD" -> generation.hard; default -> generation.medium; }).shouldBe(selected);
            (switch (defaults.get("questionType").toString()) { case "SINGLE_CHOICE" -> generation.singleChoice; case "MULTIPLE_CHOICE" -> generation.multipleChoice; default -> generation.trueFalse; }).shouldBe(selected);
            generation.selectedTypes.shouldHave(size(1));
        }
    }
}
