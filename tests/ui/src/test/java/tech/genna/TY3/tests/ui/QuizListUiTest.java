package tech.genna.TY3.tests.ui;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.Keys;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.Filter;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpResponse;
import tech.genna.TY3.tests.ui.pages.RegisterPage;
import tech.genna.TY3.tests.ui.pages.TestsPage;
import tech.genna.TY3.tests.ui.pages.QuizAttemptPage;
import tech.genna.TY3.tests.ui.support.BrowserSettings;

import static com.codeborne.selenide.CollectionCondition.*;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Browser checks with controlled quiz HTTP responses. Registration uses the running application. */
public class QuizListUiTest {
    private static final String ALGEBRA = "Алгебра 1";
    private static final String SQL = "Базы данных 1";
    private static final String HISTORY = "История 1";
    private final TestsPage page = new TestsPage();
    private final AtomicReference<Supplier<HttpResponse>> listResponse = new AtomicReference<>();
    private final AtomicInteger mutations = new AtomicInteger();
    private NetworkInterceptor interceptor;
    private volatile int mutationStatus = 500;
    private Map<String, Object> startedAttempt;
    private final String createdAttemptId = UUID.randomUUID().toString();
    private final AtomicInteger attemptStarts = new AtomicInteger();
    private final AtomicReference<String> attemptKey = new AtomicReference<>();

    @BeforeEach
    void registerUser() throws Exception {
        BrowserSettings.configure();
        Configuration.pageLoadStrategy = "eager";
        closeWebDriver();
        String username = "qa_quiz_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new RegisterPage().openPage().register(username, username + "@example.com",
                UUID.randomUUID() + "Aa1!");
        listResponse.set(QuizListUiTest::fixtureList);
        interceptor = new NetworkInterceptor(getWebDriver(), (Filter) next -> request -> {
            String path = URI.create(request.getUri()).getPath();
            if (startedAttempt != null && path.equals("/api/quizzes/a/attempts") && request.getMethod() == HttpMethod.POST) {
                attemptStarts.incrementAndGet();
                attemptKey.set(request.getHeader("Idempotency-Key"));
                return json(201, startedAttempt);
            }
            if (startedAttempt != null && path.equals("/api/attempts/" + createdAttemptId)
                    && request.getMethod() == HttpMethod.GET) {
                return json(200, startedAttempt);
            }
            if (path.equals("/api/quizzes") && request.getMethod() == HttpMethod.GET) {
                return listResponse.get().get();
            }
            if (path.startsWith("/api/quizzes/") &&
                    (request.getMethod() == HttpMethod.PATCH || request.getMethod() == HttpMethod.DELETE)) {
                mutations.incrementAndGet();
                return json(mutationStatus, Map.of("code", "QA_FIXTURE"));
            }
            return next.execute(request);
        });
    }

    @AfterEach
    void closeBrowser() {
        try {
            if (interceptor != null) interceptor.close();
        } finally {
            closeWebDriver();
        }
    }

    @Test
    @DisplayName("TC-TESTS-01: поля таблицы и действия сохранённых тестов")
    void rendersTable() {
        openList();
        $$("app-tests thead th").shouldHave(exactTexts("Название теста", "Учебный материал",
                "Количество вопросов", "Сложность", "Тип вопросов", "Дата создания", "Действия"));
        assertRow(ALGEBRA, "Линейная алгебра", "5", "Лёгкая", "Один ответ", "20.10.2026");
        assertRow(SQL, "Основы SQL", "10", "Средняя", "Несколько ответов", "22.10.2026");
        assertRow(HISTORY, "Античный мир", "5", "Сложная", "Верно / неверно", "21.10.2026");
    }

    @ParameterizedTest(name = "TC-TESTS-02: ответ списка {0}")
    @ValueSource(ints = {200, 401, 500})
    void rendersListStates(int status) {
        listResponse.set(() -> json(status, status == 200 ? Map.of("items", List.of()) : Map.of("code", "QA_FIXTURE")));
        if (status == 401) open("/tests");
        else page.openPage();
        page.rows.shouldHave(size(0));
        switch (status) {
            case 200 -> heading("Тестов пока нет").shouldBe(visible);
            case 401 -> assertSignedOut();
            case 500 -> {
                heading("Не удалось загрузить тесты").shouldBe(visible);
                heading("Тестов пока нет").shouldNot(exist);
                listResponse.set(QuizListUiTest::fixtureList);
                $x("//button[normalize-space()='Повторить']").click();
                page.rows.shouldHave(size(3));
            }
            default -> throw new IllegalArgumentException("Unexpected fixture status " + status);
        }
    }

    @Test
    @DisplayName("TC-TESTS-02: индикатор до получения ответа")
    void showsLoadingBeforeResponse() {
        var release = new CountDownLatch(1);
        listResponse.set(() -> {
            try {
                if (!release.await(20, TimeUnit.SECONDS)) return json(500, Map.of("code", "QA_TIMEOUT"));
                return fixtureList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return json(500, Map.of("code", "QA_INTERRUPTED"));
            }
        });
        try {
            page.openPage();
            heading("Загружаем тесты…").shouldBe(visible);
            heading("Тестов пока нет").shouldNot(exist);
        } finally {
            release.countDown();
        }
        page.rows.shouldHave(size(3));
    }

    @Test
    @DisplayName("TC-TESTS-03: поиск по названию и восстановление порядка")
    void filtersAndClearsSearch() {
        openList();
        sort("createdAt:desc");
        for (String query : List.of(ALGEBRA, "гебр", "АЛГЕБРА")) {
            page.searchInput.setValue(query);
            names(ALGEBRA);
        }
        for (String query : List.of("Основы SQL", "Несуществующий тест")) {
            page.searchInput.setValue(query);
            heading("По вашему запросу тесты не найдены").shouldBe(visible);
            page.rows.shouldHave(size(0));
        }
        $x("//button[normalize-space()='Очистить поиск']").click();
        names(SQL, HISTORY, ALGEBRA);
    }

    @ParameterizedTest(name = "TC-TESTS-04: {0}")
    @CsvSource({
        "name:asc, Алгебра 1, Базы данных 1, История 1",
        "name:desc, История 1, Базы данных 1, Алгебра 1",
        "materialName:asc, История 1, Алгебра 1, Базы данных 1",
        "materialName:desc, Базы данных 1, Алгебра 1, История 1",
        "createdAt:asc, Алгебра 1, История 1, Базы данных 1",
        "createdAt:desc, Базы данных 1, История 1, Алгебра 1"
    })
    void sortsList(String mode, String first, String second, String third) {
        openList();
        sort(mode);
        names(first, second, third);
        page.searchInput.setValue("1");
        names(first, second, third);
    }

    @Test
    @DisplayName("TC-TESTS-05/08: отмена переименования и удаления без запроса записи")
    void cancelsChanges() {
        openList();
        action(SQL, "Переименовать");
        $("#test-rename").shouldHave(value(SQL)).setValue("Не сохранять");
        dialogButton("Отмена").click();
        $("app-test-dialog").shouldNot(exist);
        action(SQL, "Удалить");
        $("app-test-dialog").shouldHave(text(SQL));
        dialogButton("Отмена").click();
        $("app-test-dialog").shouldNot(exist);
        row(SQL).shouldBe(visible);
        assertEquals(0, mutations.get());
    }

    @ParameterizedTest(name = "TC-TESTS-06: пробелы в названии {index}")
    @ValueSource(strings = {"", "   "})
    void rejectsBlankName(String name) {
        openList();
        action(SQL, "Переименовать");
        var input = $("#test-rename").shouldHave(value(SQL));
        if (name.isEmpty()) {
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        } else {
            input.setValue(name);
        }
        input.shouldHave(exactValue(name));
        dialogButton("Сохранить").click();
        $("app-test-dialog [role=alert]").shouldHave(text("Название должно содержать от 1 до 100 символов"));
        assertEquals(0, mutations.get());
    }

    @ParameterizedTest(name = "TC-TESTS-07: ошибка переименования {0}")
    @CsvSource({
        "400, Проверьте название теста",
        "401, Сессия завершена",
        "404, Тест не найден или недоступен",
        "409, Тест с таким названием уже существует",
        "500, Не удалось сохранить название"
    })
    void preservesRowOnRenameError(int status, String message) {
        mutationStatus = status;
        openList();
        action(SQL, "Переименовать");
        String input = status == 409 ? ALGEBRA : "Несохранённое название";
        $("#test-rename").setValue(input);
        dialogButton("Сохранить").click();
        if (status == 401) {
            assertSignedOut();
            assertEquals(1, mutations.get());
            return;
        }
        $("app-test-dialog [role=alert]").shouldHave(text(message));
        $("#test-rename").shouldHave(value(input));
        dialogButton("Отмена").click();
        row(SQL).shouldBe(visible);
        assertEquals(1, mutations.get());
    }

    @ParameterizedTest(name = "TC-TESTS-08: ошибка удаления {0}")
    @CsvSource({"401, Сессия завершена", "404, Тест не найден или недоступен", "500, Не удалось удалить тест"})
    void preservesRowOnDeleteError(int status, String message) {
        mutationStatus = status;
        openList();
        action(SQL, "Удалить");
        dialogButton("Удалить").click();
        if (status == 401) {
            assertSignedOut();
            assertEquals(1, mutations.get());
            return;
        }
        $("app-test-dialog [role=alert]").shouldHave(text(message));
        dialogButton("Отмена").click();
        row(SQL).shouldBe(visible);
        assertEquals(1, mutations.get());
    }

    @Test
    @DisplayName("FE119: выход, возврат назад и прямые ссылки не показывают личные данные")
    void protectsPrivateRoutesAfterLogout() {
        openList();
        $(".logout").click();
        assertSignedOut();
        back();
        $("app-tests").shouldNot(exist);
        for (String path : List.of("/tests", "/materials", "/materials/upload", "/dashboard",
                "/materials/11111111-1111-1111-1111-111111111111/generate")) {
            open(path);
            assertSignedOut();
            $("app-materials-shell").shouldNot(exist);
        }
        refresh();
        assertSignedOut();
    }

    private void assertSignedOut() {
        $("#username").shouldBe(visible);
        $("app-tests").shouldNot(exist);
        $("app-test-dialog").shouldNot(exist);
        assertNull(executeJavaScript("return sessionStorage.getItem('exam-ai-session')"));
    }

    @ParameterizedTest(name = "TC-TESTS-09: переход к материалам, пустой список = {0}")
    @ValueSource(booleans = {true, false})
    void opensMaterials(boolean empty) {
        if (empty) listResponse.set(() -> json(200, Map.of("items", List.of())));
        page.openPage();
        if (empty) heading("Тестов пока нет").shouldBe(visible);
        $("a.materials-link").click();
        heading("Мои материалы").shouldBe(visible);
    }

    @Test
    @DisplayName("TC-TESTS-10: запуск открывает новую попытку")
    void opensCreatedAttempt() {
        var questions = java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(position -> Map.of(
                "id", new UUID(0, position).toString(), "position", position,
                "type", "SINGLE_CHOICE", "text", "Вопрос QA " + position,
                "options", List.of(Map.of("id", new UUID(1, 1).toString(), "text", "Вариант A"),
                        Map.of("id", new UUID(1, 2).toString(), "text", "Вариант B")))).toList();
        startedAttempt = Map.of("attempt", Map.of("id", createdAttemptId, "status", "IN_PROGRESS",
                        "quiz", Map.of("id", "a", "name", ALGEBRA)),
                "questions", questions, "answers", List.of(), "currentPosition", 1);
        openList();
        page.firstStartButton.click();
        var attempt = new QuizAttemptPage();
        attempt.title.shouldBe(visible);
        attempt.position.shouldHave(exactText("Вопрос 1 из 5"));
        attempt.question.shouldHave(text("Вопрос QA 1"));
        attempt.selectedOptions.shouldHave(size(0));
        assertEquals("/attempts/" + createdAttemptId, URI.create(com.codeborne.selenide.WebDriverRunner.url()).getPath());
        assertEquals(1, attemptStarts.get());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> UUID.fromString(attemptKey.get()));
        assertEquals(0, mutations.get());
    }

    private void openList() {
        page.openPage();
        page.demoNotice.shouldNot(exist);
        page.rows.shouldHave(size(3));
    }

    private void assertRow(String name, String material, String count, String difficulty, String type, String date) {
        var row = row(name);
        row.$$("td").shouldHave(texts(material, count, difficulty, type, date, "Пройти тест"));
        row.$("button[title='Переименовать']").shouldBe(enabled);
        row.$("button[title='Удалить']").shouldBe(enabled);
    }

    private SelenideElement row(String name) {
        return page.rows.findBy(text(name));
    }

    private void action(String name, String title) {
        row(name).$("button[title='" + title + "']").click();
    }

    private void names(String... expected) {
        $$("app-tests tbody .test-name span:last-child").shouldHave(exactTexts(expected));
    }

    private void sort(String value) {
        $("select[aria-label='Сортировка тестов']").selectOptionByValue(value);
    }

    private static SelenideElement heading(String title) {
        return $x("//*[self::h1 or self::h2][normalize-space()='" + title + "']");
    }

    private static SelenideElement dialogButton(String label) {
        return $x("//app-test-dialog//button[normalize-space()='" + label + "']");
    }

    private static HttpResponse fixtureList() {
        return json(200, Map.of("items", List.of(
            fixture("a", ALGEBRA, "Линейная алгебра", 5, "EASY", "SINGLE_CHOICE", "20"),
            fixture("b", SQL, "Основы SQL", 10, "MEDIUM", "MULTIPLE_CHOICE", "22"),
            fixture("c", HISTORY, "Античный мир", 5, "HARD", "TRUE_FALSE", "21")
        )));
    }

    private static Map<String, Object> fixture(String id, String name, String material, int count,
            String difficulty, String type, String day) {
        return Map.of("id", id, "name", name, "materialName", material, "format", "TXT",
                "questionCount", count, "difficulty", difficulty, "questionType", type,
                "createdAt", "2026-10-" + day + "T00:00:00Z");
    }

    private static HttpResponse json(int status, Object body) {
        return new HttpResponse().setStatus(status).addHeader("Content-Type", "application/json; charset=UTF-8")
                .setContent(Contents.utf8String(new Json().toJson(body)));
    }
}
