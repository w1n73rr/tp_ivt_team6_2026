package tech.genna.TY3.tests.ui;

import tech.genna.TY3.tests.ui.support.BrowserSettings;
import tech.genna.TY3.tests.ui.pages.DashboardPage;
import java.util.Locale;
import static com.codeborne.selenide.Condition.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import tech.genna.TY3.tests.ui.pages.LoginPage;
import tech.genna.TY3.tests.ui.pages.MaterialsPage;
import tech.genna.TY3.tests.ui.pages.RegisterPage;
import tech.genna.TY3.tests.ui.pages.UploadPage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.openqa.selenium.remote.http.HttpMethod.POST;

public class MaterialUploadUiTest {
    private static final String SUBJECT = "Базы данных";
    private static final String SEED_NAME = "Подготовка предмета";
    private static final String SUCCESS_NAME = "Загрузка материала 001";
    private static final String NO_FILE_NAME = "Материал без файла";
    private static final String BAD_FORMAT_NAME = "Недопустимый формат";
    private static final String SERVER_ERROR_NAME = "Загрузка с ошибкой сервера";
    private static final String PASSWORD = "QaUpload2026!Local";
    private static final MaterialsPage MATERIALS = new MaterialsPage();
    private static String username;
    private static File lecture;
    private static File image;
    private static Path fixturesDir;
    private static boolean accountCreated;

    @BeforeAll
    static void prepareUserAndSubject() throws IOException {
        BrowserSettings.configure();

        fixturesDir = Files.createTempDirectory("material-upload-ui-");
        lecture = fixturesDir.resolve("lecture.txt").toFile();
        String sample = "Базы данных. SQL SELECT, INSERT и JOIN.\n";
        Files.writeString(lecture.toPath(), sample.repeat(400), StandardCharsets.UTF_8);
        image = fixturesDir.resolve("image.png").toFile();
        Files.write(image.toPath(), Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lZkAAAAASUVORK5CYII="));

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        username = "qa_upload_" + suffix;
        new RegisterPage().openPage().register(username, username + "@example.com", PASSWORD);
        accountCreated = true;

        MATERIALS.openPage().openUploadForm()
                .chooseFile(lecture)
                .name(SEED_NAME)
                .createSubject(SUBJECT)
                .submit()
                .shouldShowSuccess()
                .backToMaterials()
                .shouldContain(SEED_NAME);
    }

    @BeforeEach
    void logInWithFreshBrowser() {
        closeWebDriver();
        logIn();
    }

    @AfterEach
    void closeBrowser() {
        closeWebDriver();
    }

    @AfterAll
    static void removeTestMaterials() throws IOException {
        try {
            if (accountCreated) {
                logIn();
                MATERIALS.openPage();
                MATERIALS.deleteIfPresent(SUCCESS_NAME);
                MATERIALS.deleteIfPresent(SEED_NAME);
            }
        } finally {
            closeWebDriver();
            if (lecture != null) Files.deleteIfExists(lecture.toPath());
            if (image != null) Files.deleteIfExists(image.toPath());
            if (fixturesDir != null) Files.deleteIfExists(fixturesDir);
        }
    }

    @Test
    @DisplayName("TC-UPL-01: успешная загрузка TXT через форму")
    void successfulUpload() throws IOException {
        MaterialsPage materialsPage = new MaterialsPage().openPage();
        materialsPage.row(SUCCESS_NAME).shouldNot(exist);
        materialsPage.uploadButton.click();
        UploadPage uploadPage = new UploadPage().shouldBeOpen();

        uploadPage.fileInput.uploadFile(lecture);
        String size = String.format(Locale.US, "%.2f", Files.size(lecture.toPath()) / 1048576.0);
        uploadPage.fileCard.shouldBe(visible).shouldHave(text("lecture.txt"), text("TXT"), text(size + " МБ"));
        uploadPage.nameInput.setValue(SUCCESS_NAME).shouldHave(value(SUCCESS_NAME));
        uploadPage.subjectSelect.selectOption(SUBJECT);
        uploadPage.subjectSelect.shouldHave(value(SUBJECT));
        uploadPage.descriptionInput.setValue("Материал для проверки загрузки.")
                .shouldHave(value("Материал для проверки загрузки."));
        uploadPage.tagInput.setValue("SQL");
        uploadPage.addTagButton.click();
        uploadPage.tag.shouldHave(text("SQL"));

        uploadPage.submitButton.click();
        uploadPage.successTitle.shouldBe(visible).shouldHave(exactText("Материал сохранён на сервере"));
        uploadPage.successPanel.shouldHave(text("Идентификатор материала:"));
        uploadPage.successReturnLink.click();
        materialsPage.shouldBeOpen();
        materialsPage.row(SUCCESS_NAME).shouldBe(visible).shouldHave(text(SUBJECT), text("TXT"));
    }

    @Test
    @DisplayName("TC-UPL-02: нельзя загрузить материал без файла")
    void uploadWithoutFile() {
        MaterialsPage materialsPage = new MaterialsPage().openPage();
        materialsPage.uploadButton.click();
        UploadPage uploadPage = new UploadPage().shouldBeOpen();
        uploadPage.nameInput.setValue(NO_FILE_NAME).shouldHave(value(NO_FILE_NAME));
        uploadPage.subjectSelect.selectOption(SUBJECT);
        uploadPage.subjectSelect.shouldHave(value(SUBJECT));
        uploadPage.fileCard.shouldNot(exist);
        uploadPage.submitButton.click();
        uploadPage.errorMessage.shouldBe(visible).shouldHave(text("Выберите файл для загрузки"));
        uploadPage.successPanel.shouldNot(exist);
        uploadPage.materialsLink.click();
        materialsPage.shouldBeOpen().row(NO_FILE_NAME).shouldNot(exist);
    }

    @Test
    @DisplayName("TC-UPL-03: PNG отклоняется формой")
    void forbiddenFormat() {
        MaterialsPage materialsPage = new MaterialsPage().openPage();
        materialsPage.uploadButton.click();
        UploadPage uploadPage = new UploadPage().shouldBeOpen();
        uploadPage.fileInput.uploadFile(image);
        uploadPage.errorMessage.shouldBe(visible).shouldHave(text("Поддерживаются только PDF, DOCX и TXT"));
        uploadPage.fileCard.shouldNot(exist);
        uploadPage.nameInput.setValue(BAD_FORMAT_NAME);
        uploadPage.subjectSelect.selectOption(SUBJECT);
        uploadPage.submitButton.click();
        uploadPage.errorMessage.shouldBe(visible).shouldHave(text("Выберите файл для загрузки"));
        uploadPage.successPanel.shouldNot(exist);
        uploadPage.materialsLink.click();
        materialsPage.shouldBeOpen().row(BAD_FORMAT_NAME).shouldNot(exist);
    }

    @Test
    @DisplayName("TC-UPL-04: ошибка 500 при загрузке показана пользователю")
    void serverError() {
        MaterialsPage materialsPage = new MaterialsPage().openPage();
        materialsPage.uploadButton.click();
        UploadPage uploadPage = new UploadPage().shouldBeOpen();
        uploadPage.fileInput.uploadFile(lecture);
        uploadPage.fileCard.shouldBe(visible).shouldHave(text("lecture.txt"));
        uploadPage.nameInput.setValue(SERVER_ERROR_NAME).shouldHave(value(SERVER_ERROR_NAME));
        uploadPage.subjectSelect.selectOption(SUBJECT);
        uploadPage.subjectSelect.shouldHave(value(SUBJECT));

        Route failUpload = Route.matching(request -> POST == request.getMethod()
                        && request.getUri().endsWith("/api/materials"))
                .to(() -> request -> new HttpResponse().setStatus(500)
                        .addHeader("Content-Type", "application/json")
                        .setContent(Contents.utf8String("{\"error\":\"Test server error\"}")));
        try (NetworkInterceptor ignored = new NetworkInterceptor(getWebDriver(), failUpload)) {
            uploadPage.submitButton.click();
            uploadPage.errorMessage.shouldBe(visible).shouldHave(text("Не удалось загрузить материал"));
            uploadPage.successPanel.shouldNot(exist);
        }
        uploadPage.materialsLink.click();
        materialsPage.shouldBeOpen().row(SERVER_ERROR_NAME).shouldNot(exist);
    }

    private static void logIn() {
        LoginPage loginPage = new LoginPage().openPage();
        loginPage.usernameInput.setValue(username);
        loginPage.passwordInput.setValue(PASSWORD);
        loginPage.submitButton.click();
        new DashboardPage().materialsLink.shouldBe(visible);
    }
}
