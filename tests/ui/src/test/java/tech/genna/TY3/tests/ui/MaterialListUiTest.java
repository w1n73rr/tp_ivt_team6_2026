package tech.genna.TY3.tests.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import tech.genna.TY3.tests.ui.pages.*;
import tech.genna.TY3.tests.ui.support.BrowserSettings;
import tech.genna.TY3.tests.ui.support.MaterialListFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.CollectionCondition.textsInAnyOrder;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.junit.jupiter.api.Assertions.*;
import static org.openqa.selenium.remote.http.HttpMethod.GET;

public class MaterialListUiTest {
    private static final String M1 = "Алгебра";
    private static final String M2 = "Базы данных";
    private static final String M3 = "Введение в SQL";
    private static final String M4 = "Физика";
    private static final String PASSWORD = "QaView2026!Local";
    private final MaterialsPage materialsPage = new MaterialsPage();
    private final List<String> accounts = new ArrayList<>();
    private final Map<String, ExpectedMaterial> expected = new LinkedHashMap<>();
    private MaterialListFiles files;
    private String userA;

    @BeforeEach
    void prepareUserWithoutMaterials() throws IOException {
        BrowserSettings.configure();
        closeWebDriver();
        files = new MaterialListFiles();
        userA = registerUser();
    }

    @AfterEach
    void removeTestData() throws IOException {
        try {
            for (String account : accounts) {
                new LoginPage().openPage().signIn(account, PASSWORD);
                materialsPage.openPage();
                for (String name : new String[]{M1, M2, M3, M4}) materialsPage.deleteIfPresent(name);
                materialsPage.materialRows.shouldHave(size(0));
            }
        } finally {
            closeWebDriver();
            if (files != null) files.close();
        }
    }

    @Test
    @DisplayName("TC-VIEW-01: три материала без дублей, счётчик соответствует списку")
    void listSeveralMaterials() throws IOException {
        seedMaterialsForA();
        navigateToList();
        assertAList();
    }

    @Test
    @DisplayName("TC-VIEW-02: пустой список — нормальное состояние, загрузка доступна")
    void emptyList() {
        navigateToList();
        materialsPage.shouldBeOpen();
        materialsPage.emptyState.shouldBe(visible).shouldHave(text("Материалы не найдены"));
        materialsPage.materialRows.shouldHave(size(0));
        materialsPage.foundCount.shouldHave(exactText("Найдено материалов: 0"));
        materialsPage.listError.shouldNot(exist);
        materialsPage.uploadButton.shouldBe(visible, enabled).click();
        new UploadPage().shouldBeOpen().fileInput.should(exist);
    }

    @Test
    @DisplayName("TC-VIEW-03: ошибка 500 отличается от пустого списка, повтор восстанавливает данные")
    void retryAfterListError() throws IOException {
        seedMaterialsForA();
        AtomicInteger requests = new AtomicInteger();
        Route failedList = Route.matching(request -> GET == request.getMethod()
                        && request.getUri().matches("(?:https?://[^/]+)?/api/materials(?:\\?.*)?"))
                .to(() -> request -> {
                    requests.incrementAndGet();
                    return new HttpResponse().setStatus(500).addHeader("Content-Type", "application/json")
                            .setContent(Contents.utf8String("{\"error\":\"Test list error\"}"));
                });
        try (NetworkInterceptor ignored = new NetworkInterceptor(getWebDriver(), failedList)) {
            navigateToList();
            materialsPage.listError.shouldBe(visible).shouldHave(text("Не удалось загрузить материалы. Попробуйте ещё раз."));
            materialsPage.retryButton.shouldBe(visible, enabled);
            materialsPage.emptyState.shouldNot(exist);
            materialsPage.list.shouldNot(exist);
            materialsPage.foundCount.shouldNot(exist);
            assertEquals(1, requests.get(), "Получение списка должно получить ответ 500");
        }
        materialsPage.retryButton.click();
        assertAList();
        materialsPage.listError.shouldNot(exist);
        materialsPage.retryButton.shouldNot(exist);
    }

    @Test
    @DisplayName("TC-VIEW-04: название, предмет, формат, индикатор, размер и дата PDF/DOCX/TXT")
    void showCorrectMetadata() throws IOException {
        seedMaterialsForA();
        navigateToList();
        assertAList();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
        for (ExpectedMaterial data : expected.values()) {
            MaterialRow row = materialsPage.material(data.name());
            row.title.shouldHave(exactText(data.name()));
            row.metadata.shouldHave(exactText(data.subject() + " · " + data.format() + " · " + data.sizeLabel()));
            row.format.shouldBe(visible).shouldHave(exactText(data.format()));
            // In the current design the file icon is a styled badge with its format abbreviation.
            if (data.format().equals("DOCX")) row.format.shouldHave(cssClass("docx"));
            else if (data.format().equals("TXT")) row.format.shouldHave(cssClass("txt"));
            else row.format.shouldNotHave(cssClass("docx"), cssClass("txt"));
            row.uploadedAt.shouldBe(visible).shouldHave(matchText("^\\d{2}\\.\\d{2}\\.\\d{4}$"));
            LocalDate shownDate = LocalDate.parse(row.uploadedAt.getText(), dateFormat);
            assertFalse(shownDate.isBefore(data.before()) || shownDate.isAfter(data.after()),
                    "Дата должна соответствовать времени загрузки в UTC: " + data.name());
        }
    }

    @Test
    @DisplayName("TC-VIEW-05: после смены аккаунта и обновления видны только собственные материалы")
    void isolateUsersAfterLogout() throws IOException {
        seedMaterialsForA();
        logout();
        String userB = registerUser();
        upload(M4, "Физика", files.txt, "TXT", true);
        logout();
        new LoginPage().openPage().signIn(userA, PASSWORD);
        new DashboardPage().materialsLink.click();
        assertAList();
        materialsPage.accountName.shouldHave(exactText(userA));
        materialsPage.material(M4).row.shouldNot(exist);

        logout();
        new LoginPage().openPage().signIn(userB, PASSWORD);
        new DashboardPage().materialsLink.click();
        assertBList(userB);
        refresh();
        assertBList(userB);
    }

    private void assertAList() {
        materialsPage.shouldBeOpen();
        materialsPage.searchInput.shouldHave(domProperty("value", ""));
        materialsPage.formatSelect.shouldHave(domProperty("value", ""));
        materialsPage.materialRows.shouldHave(size(3));
        materialsPage.materialTitles.shouldHave(textsInAnyOrder(M1, M2, M3));
        assertEquals(3, materialsPage.materialTitles.texts().stream().distinct().count(), "Дубли названий отсутствуют");
        for (String name : new String[]{M1, M2, M3}) {
            materialsPage.material(name).title.shouldBe(visible).shouldHave(exactText(name));
        }
        materialsPage.foundCount.shouldHave(exactText("Найдено материалов: 3"));
        materialsPage.listError.shouldNot(exist);
        materialsPage.emptyState.shouldNot(exist);
    }

    private void assertBList(String userB) {
        materialsPage.shouldBeOpen();
        materialsPage.accountName.shouldHave(exactText(userB));
        materialsPage.materialRows.shouldHave(size(1));
        materialsPage.materialTitles.shouldHave(textsInAnyOrder(M4));
        materialsPage.material(M4).title.shouldBe(visible).shouldHave(exactText(M4));
        materialsPage.foundCount.shouldHave(exactText("Найдено материалов: 1"));
        for (String name : new String[]{M1, M2, M3}) materialsPage.material(name).row.shouldNot(exist);
        materialsPage.listError.shouldNot(exist);
    }

    private void navigateToList() {
        open("/dashboard");
        new DashboardPage().materialsLink.shouldBe(visible).click();
    }

    private void logout() {
        materialsPage.openPage().logoutButton.click();
        new LoginPage().usernameInput.shouldBe(visible);
        materialsPage.logoutButton.shouldNot(exist);
        assertNull(executeJavaScript("return sessionStorage.getItem('exam-ai-session')"),
                "Выход должен очистить сохранённую сессию");
    }

    private String registerUser() {
        String user = "qa_view_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new RegisterPage().openPage().register(user, user + "@example.com", PASSWORD);
        accounts.add(user);
        return user;
    }

    private void seedMaterialsForA() throws IOException {
        upload(M1, "Математика", files.pdf, "PDF", true);
        upload(M2, "Базы данных", files.docx, "DOCX", true);
        upload(M3, "Базы данных", files.txt, "TXT", false);
    }

    private void upload(String name, String subject, Path file, String format, boolean createSubject) throws IOException {
        UploadPage page = materialsPage.openPage().openUploadForm();
        page.fileInput.uploadFile(file.toFile());
        page.nameInput.setValue(name);
        if (createSubject) {
            page.subjectSelect.selectOption("Создать предмет");
            page.customSubjectInput.setValue(subject);
        } else page.subjectSelect.selectOption(subject);
        LocalDate before = LocalDate.now(ZoneOffset.UTC);
        page.submitButton.click();
        page.shouldShowSuccess();
        LocalDate after = LocalDate.now(ZoneOffset.UTC);
        expected.put(name, new ExpectedMaterial(name, subject, format, sizeLabel(Files.size(file)), before, after));
        page.successReturnLink.click();
        materialsPage.shouldBeOpen().material(name).title.shouldHave(exactText(name));
    }

    private String sizeLabel(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1048576) return Math.round(bytes / 1024.0) + " КБ";
        return "1 МБ";
    }

    private record ExpectedMaterial(String name, String subject, String format, String sizeLabel,
                                    LocalDate before, LocalDate after) {}
}
