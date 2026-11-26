package tech.genna.TY3.tests.ui;

import com.codeborne.selenide.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.http.*;
import tech.genna.TY3.tests.ui.pages.*;
import tech.genna.TY3.tests.ui.support.BrowserSettings;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.codeborne.selenide.WebDriverRunner.url;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.CollectionCondition.*;
import static org.junit.jupiter.api.Assertions.*;

/** UI state checks use controlled HTTP responses. The live-ml scenario uses real persistence. */
public class QuizResultUiTest {
    private final QuizResultPage result = new QuizResultPage();
    private final QuizAttemptPage attempt = new QuizAttemptPage();
    private final String attemptId = UUID.randomUUID().toString();
    private final String quizId = UUID.randomUUID().toString();
    private final String nextAttemptId = UUID.randomUUID().toString();
    private final AtomicInteger resultStatus = new AtomicInteger(200);
    private final AtomicInteger starts = new AtomicInteger();
    private Map<String, Object> fixture;
    private NetworkInterceptor interceptor;

    @BeforeEach
    void prepareUser() throws Exception {
        BrowserSettings.configure();
        closeWebDriver();
        // ChromeDriver protocol logs include registration payloads and authentication responses.
        System.setProperty("webdriver.chrome.verboseLogging", "false");
        System.setProperty("webdriver.chrome.logfile", System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
        // Registration failures must not save a DOM containing credentials.
        boolean screenshots = Configuration.screenshots;
        boolean saveSource = Configuration.savePageSource;
        Configuration.screenshots = false;
        Configuration.savePageSource = false;
        try {
            String user = "qa_result_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            var registration = new RegisterPage();
            open("/register");
            try {
                registration.usernameInput.shouldBe(visible, Duration.ofSeconds(45));
            } catch (AssertionError error) {
                // No credentials have been entered at this point.
                screenshot("result-registration-page-unavailable");
                throw error;
            }
            registration.register(user, user + "@example.com", UUID.randomUUID() + "Aa1!");
            new DashboardPage().title.shouldHave(exactText("Здравствуйте!"));
        } finally {
            Configuration.screenshots = screenshots;
            Configuration.savePageSource = saveSource;
        }
    }

    @AfterEach
    void closeBrowser() {
        try {
            if (interceptor != null) interceptor.close();
        } finally {
            closeWebDriver();
        }
    }

    @ParameterizedTest(name = "TC-RES-01–07: {0}, {1} вопросов")
    @CsvSource({"SINGLE_CHOICE,5", "TRUE_FALSE,10", "MULTIPLE_CHOICE,5"})
    void savedResultAndAnswers(String type, int count) {
        installFixture(type, count);
        result.openPage(attemptId);
        assertCounters(count);
        result.questions.shouldHave(size(count));
        result.question(0).outcome.shouldHave(exactText("Верно · 1 / 1"));
        result.question(count - 2).outcome.shouldHave(exactText("Неверно · 0 / 1"));
        result.question(count - 1).outcome.shouldHave(exactText("Пропущено · 0 / 1"));
        for (int i = 0; i < count; i++) {
            var question = result.question(i);
            question.text.shouldHave(exactText("Вопрос QA " + (i + 1)));
            List<String> correct = type.equals("TRUE_FALSE") ? List.of("Верно") :
                    type.equals("MULTIPLE_CHOICE") ? List.of("Вариант A", "Вариант B") : List.of("Вариант A");
            question.correctAnswers.shouldHave(exactTexts(correct));
            String wrong = type.equals("TRUE_FALSE") ? "Неверно" : "Вариант C";
            List<String> selected = i == count - 1 ? List.of() : i == count - 2 ? List.of(wrong) : correct;
            if (selected.isEmpty()) question.selectedAnswers.shouldHave(size(0));
            else question.selectedAnswers.shouldHave(exactTexts(selected));
        }
        List<String> before = result.questions.texts();
        String duration = result.duration.text();
        refresh();
        assertCounters(count);
        result.questions.shouldHave(exactTexts(before));
        result.duration.shouldHave(exactText(duration));
        assertEquals(0, starts.get(), "Открытие результата не создаёт попытку");
    }

    @Test @DisplayName("TC-RES-10/11/19: возврат, новая попытка и прежний результат")
    void returnAndRepeat() {
        installFixture("SINGLE_CHOICE", 5);
        result.openPage(attemptId);
        assertCounters(5);
        List<String> saved = result.questions.texts();
        result.backLink.click();
        new TestsPage().title.shouldHave(exactText("Тесты"));
        assertEquals(0, starts.get());
        result.openPage(attemptId);
        result.repeatButton.shouldBe(enabled).click();
        attempt.position.shouldHave(exactText("Вопрос 1 из 5"));
        assertEquals("/attempts/" + nextAttemptId, URI.create(url()).getPath());
        assertNotEquals(attemptId, nextAttemptId);
        attempt.selectedOptions.shouldHave(size(0));
        assertEquals(1, starts.get());
        result.openPage(attemptId);
        assertCounters(5);
        result.questions.shouldHave(exactTexts(saved));
    }

    @Test @DisplayName("TC-RES-14/16: недоступность и ошибка после просмотра результата")
    void unavailableResultRemovesPrivateData() {
        installFixture("SINGLE_CHOICE", 5);
        result.openPage(attemptId);
        assertCounters(5);
        resultStatus.set(404);
        refresh();
        result.unavailable.shouldBe(visible);
        assertNoPrivateResult();
        resultStatus.set(500);
        refresh();
        result.error.shouldHave(exactText("Не удалось загрузить результат."));
        assertNoPrivateResult();
        resultStatus.set(200);
        result.retryButton.click();
        assertCounters(5);
        result.error.shouldNot(exist);
    }

    @Test @DisplayName("TC-RES-13: истёкшая сессия очищает результат")
    void expiredSessionRemovesPrivateData() {
        installFixture("SINGLE_CHOICE", 5);
        result.openPage(attemptId);
        assertCounters(5);
        resultStatus.set(401);
        refresh();
        new LoginPage().usernameInput.shouldBe(visible);
        assertEquals("/login", URI.create(url()).getPath());
        assertNoPrivateResult();
    }

    @Test @DisplayName("TC-RES-15: активная попытка не раскрывает результат")
    void unfinishedAttempt() {
        installFixture("SINGLE_CHOICE", 5);
        resultStatus.set(409);
        result.openPage(attemptId);
        result.unfinished.shouldBe(visible);
        assertNoPrivateResult();
    }

    @Test @DisplayName("TC-RES-18: текст вопроса выводится безопасно")
    void rendersTextSafely() {
        installFixture("SINGLE_CHOICE", 5);
        String payload = "<img src=x onerror=alert('qa')> & <script>alert('qa')</script>";
        items(fixture).get(0).put("question", question(1, "SINGLE_CHOICE", payload));
        result.openPage(attemptId);
        result.question(0).text.shouldHave(exactText(payload));
        assertEquals(0L, (long) executeJavaScript("return document.querySelectorAll('.result article img,.result article script').length"));
    }

    @Test @Tag("live-ml") @DisplayName("Реальный BE/ML: сохранение результата, ответы, обновление и повтор")
    void liveSavedResultAndRepeat() throws Exception {
        Path lecture = Files.createTempFile("qa-result-", ".txt");
        try {
            try (var input = getClass().getResourceAsStream("/generation/lecture.txt")) {
                assertNotNull(input);
                Files.write(lecture, input.readAllBytes());
            }
            String name = "QA результат " + UUID.randomUUID().toString().substring(0, 8);
            var materials = new MaterialsPage();
            materials.openPage().openUploadForm().chooseFile(lecture.toFile()).name(name)
                    .createSubject("Тестирование ПО").submit().shouldShowSuccess().backToMaterials();
            materials.material(name).generateButton.click();
            var generation = new GenerationPage().shouldBeOpen();
            generation.fiveQuestions.click();
            generation.singleChoice.click();
            generation.createButton.click();
            generation.title.shouldHave(exactText("Тест готов"), Duration.ofMinutes(8));
            generation.readyLink.click();
            var tests = new TestsPage();
            tests.rows.shouldHave(size(1));
            tests.rows.first().shouldHave(text(name));
            tests.firstStartButton.click();
            // Complete a calibration attempt to obtain the saved answer key through the result UI.
            for (int i = 1; i <= 5; i++) {
                attempt.position.shouldHave(exactText("Вопрос " + i + " из 5"));
                if (i < 5) attempt.nextButton.click();
            }
            attempt.finishButton.click();
            attempt.confirmButton.click();
            result.questions.shouldHave(size(5));
            Map<String, String> answers = new HashMap<>();
            for (int i = 0; i < 5; i++) answers.put(result.question(i).text.text(), result.question(i).correctAnswers.first().text());
            result.repeatButton.click();
            for (int i = 1; i <= 5; i++) {
                attempt.position.shouldHave(exactText("Вопрос " + i + " из 5"));
                String questionText = attempt.question.text().replaceFirst("(?s)^ВОПРОС \\d+\\s*", "").trim();
                String correct = answers.get(questionText);
                assertNotNull(correct, "Повтор должен содержать вопросы сохранённого теста");
                if (i <= 3) attempt.options.findBy(exactText(correct)).click();
                if (i == 4) attempt.options.findBy(not(exactText(correct))).click();
                if (i < 5) attempt.nextButton.click();
            }
            attempt.finishButton.click();
            attempt.confirmButton.click();
            assertCounters(5);
            String savedUrl = url();
            List<String> saved = result.questions.texts();
            refresh();
            assertCounters(5);
            result.questions.shouldHave(exactTexts(saved));
            result.backLink.click();
            new TestsPage().title.shouldHave(exactText("Тесты"));
            open(savedUrl);
            result.repeatButton.shouldBe(enabled).click();
            attempt.position.shouldHave(exactText("Вопрос 1 из 5"));
            assertNotEquals(URI.create(savedUrl).getPath().replace("/result", ""), URI.create(url()).getPath());
            attempt.selectedOptions.shouldHave(size(0));
            open(savedUrl);
            assertCounters(5);
            result.questions.shouldHave(exactTexts(saved));
        } finally {
            Files.deleteIfExists(lecture);
        }
    }

    private void assertCounters(int count) {
        result.percent.shouldHave(exactText(count == 5 ? "60,0%" : "80,0%"));
        result.correctCount.shouldHave(exactText(Integer.toString(count - 2)));
        result.incorrectCount.shouldHave(exactText("1"));
        result.skippedCount.shouldHave(exactText("1"));
        result.totalCount.shouldHave(exactText(Integer.toString(count)));
        result.score.shouldHave(exactText("Вы набрали " + (count - 2) + ",0 из " + count + " баллов."));
    }

    private void assertNoPrivateResult() {
        result.percent.shouldNot(exist);
        result.quizName.shouldNot(exist);
        result.questions.shouldHave(size(0));
        result.repeatButton.shouldNot(exist);
    }

    private void installFixture(String type, int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String a = new UUID(1, 1).toString();
            String b = new UUID(1, 2).toString();
            String wrong = new UUID(1, type.equals("TRUE_FALSE") ? 2 : 3).toString();
            List<String> key = type.equals("MULTIPLE_CHOICE") ? List.of(a, b) : List.of(a);
            List<String> selected = i == count ? List.of() : i == count - 1 ? List.of(wrong) : key;
            rows.add(new HashMap<>(Map.of("question", question(i, type, "Вопрос QA " + i),
                    "selectedOptionIds", selected, "correctOptionIds", key,
                    "outcome", i == count ? "SKIPPED" : i == count - 1 ? "INCORRECT" : "CORRECT",
                    "score", i < count - 1 ? 1 : 0)));
        }
        fixture = new HashMap<>(Map.of("attemptId", attemptId, "quiz", Map.of("id", quizId, "name", "Синтетический тест QA",
                "subjectName", "Тестирование ПО", "difficulty", "MEDIUM", "questionType", type),
                "items", rows, "score", count - 2, "scorePercent", count == 5 ? 60 : 80,
                "totalQuestions", count, "correctCount", count - 2, "incorrectCount", 1, "skippedCount", 1,
                "durationSeconds", 765));
        fixture.put("completedAt", "2026-11-26T12:00:00Z");
        interceptor = new NetworkInterceptor(getWebDriver(), (Filter) next -> request -> {
            String path = URI.create(request.getUri()).getPath();
            if (path.equals("/api/attempts/" + attemptId + "/result") && request.getMethod() == HttpMethod.GET) {
                int status = resultStatus.get();
                return json(status, status == 200 ? fixture : Map.of("code", "QA_TEST_RESPONSE"));
            }
            if (path.equals("/api/quizzes/" + quizId + "/attempts") && request.getMethod() == HttpMethod.POST) {
                starts.incrementAndGet();
                return json(201, detail(rows));
            }
            if (path.equals("/api/attempts/" + nextAttemptId) && request.getMethod() == HttpMethod.GET) return json(200, detail(rows));
            return next.execute(request);
        });
    }

    private Map<String, Object> detail(List<Map<String, Object>> rows) {
        return Map.of("attempt", Map.of("id", nextAttemptId, "status", "IN_PROGRESS", "quiz", fixture.get("quiz")),
                "questions", rows.stream().map(row -> row.get("question")).toList(), "answers", List.of(), "currentPosition", 1);
    }

    private static Map<String, Object> question(int position, String type, String text) {
        List<Map<String, String>> options = new ArrayList<>();
        options.add(Map.of("id", new UUID(1, 1).toString(), "text", type.equals("TRUE_FALSE") ? "Верно" : "Вариант A"));
        options.add(Map.of("id", new UUID(1, 2).toString(), "text", type.equals("TRUE_FALSE") ? "Неверно" : "Вариант B"));
        if (!type.equals("TRUE_FALSE")) options.add(Map.of("id", new UUID(1, 3).toString(), "text", "Вариант C"));
        return Map.of("id", new UUID(0, position).toString(), "position", position, "type", type, "text", text,
                "options", options);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> value) {
        return (List<Map<String, Object>>) value.get("items");
    }

    private static HttpResponse json(int status, Object body) {
        return new HttpResponse().setStatus(status).addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Cache-Control", "no-store").setContent(Contents.utf8String(new Json().toJson(body)));
    }
}
