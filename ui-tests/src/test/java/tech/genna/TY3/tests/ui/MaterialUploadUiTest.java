package tech.genna.TY3.tests.ui;

import com.codeborne.selenide.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
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

/** Browser checks for tpivt6_26-46. Requires the Docker frontend, backend and storage. */
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
        Configuration.baseUrl = System.getProperty("ui.baseUrl", "http://localhost:3000");
        Configuration.browser = System.getProperty("ui.browser", "chrome");
        Configuration.headless = Boolean.parseBoolean(System.getProperty("ui.headless", "true"));
        Configuration.browserSize = "1366x900";
        Configuration.timeout = 15_000;
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-gpu", "--no-first-run");
        if (Boolean.getBoolean("ui.disableSandbox")) {
            options.addArguments("--no-sandbox", "--disable-gpu-sandbox");
        }
        Configuration.browserCapabilities = options;

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

        // Create the required subject through the real UI once for this isolated user.
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
        MATERIALS.openPage().shouldNotContain(SUCCESS_NAME);
        MATERIALS.openUploadForm()
                .chooseFile(lecture)
                .shouldShowSelectedFile(lecture, "TXT")
                .name(SUCCESS_NAME)
                .subject(SUBJECT)
                .description("Материал для проверки загрузки.")
                .addTag("SQL")
                .submit()
                .shouldShowSuccess()
                .backToMaterials()
                .shouldContain(SUCCESS_NAME, SUBJECT, "TXT");
    }

    @Test
    @DisplayName("TC-UPL-02: нельзя загрузить материал без файла")
    void uploadWithoutFile() {
        MATERIALS.openPage().openUploadForm()
                .name(NO_FILE_NAME)
                .subject(SUBJECT)
                .shouldHaveNoSelectedFile()
                .submit()
                .shouldShowError("Выберите файл для загрузки")
                .shouldNotShowSuccess()
                .backToMaterials()
                .shouldNotContain(NO_FILE_NAME);
    }

    @Test
    @DisplayName("TC-UPL-03: PNG отклоняется формой")
    void forbiddenFormat() {
        MATERIALS.openPage();
        // Selenium sets the file input directly, even though the OS chooser filters by extension.
        MATERIALS.openUploadForm()
                .chooseFile(image)
                .shouldShowError("Поддерживаются только PDF, DOCX и TXT")
                .shouldHaveNoSelectedFile()
                .name(BAD_FORMAT_NAME)
                .subject(SUBJECT)
                .submit()
                .shouldNotShowSuccess()
                .backToMaterials()
                .shouldNotContain(BAD_FORMAT_NAME);
    }

    @Test
    @DisplayName("TC-UPL-04: ошибка 500 при загрузке показана пользователю")
    void serverError() {
        UploadPage upload = MATERIALS.openPage().openUploadForm()
                .chooseFile(lecture)
                .name(SERVER_ERROR_NAME)
                .subject(SUBJECT);

        Route failUpload = Route.matching(request -> POST == request.getMethod()
                        && request.getUri().endsWith("/api/materials"))
                .to(() -> request -> new HttpResponse().setStatus(500)
                        .addHeader("Content-Type", "application/json")
                        .setContent(Contents.utf8String("{\"error\":\"Test server error\"}")));
        try (NetworkInterceptor ignored = new NetworkInterceptor(getWebDriver(), failUpload)) {
            upload.submit()
                    .shouldShowError("Не удалось загрузить материал")
                    .shouldNotShowSuccess();
        }
        upload.backToMaterials().shouldNotContain(SERVER_ERROR_NAME);
    }

    private static void logIn() {
        new LoginPage().openPage().signIn(username, PASSWORD);
    }
}
