package tech.genna.TY3.tests.ui;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import tech.genna.TY3.tests.ui.pages.*;
import tech.genna.TY3.tests.ui.pages.MaterialRow.Snapshot;
import tech.genna.TY3.tests.ui.support.BrowserSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openqa.selenium.remote.http.HttpMethod.PATCH;

public class MaterialEditUiTest {
    private static final String ORIGINAL_NAME = "Алгебра";
    private static final String ORIGINAL_SUBJECT = "Математика";
    private static final String OTHER_NAME = "Геометрия";
    private static final String CATALOG_SEED = "Лекция по SQL";
    private static final String NEW_SUBJECT = "Базы данных";
    private static final String PASSWORD = "QaEdit2026!Local";
    private final MaterialsPage materialsPage = new MaterialsPage();
    private Path lecture;
    private boolean registered;
    private Snapshot original;
    private Snapshot other;
    private Snapshot seed;

    @BeforeEach
    void prepareIndependentMaterials() throws IOException {
        BrowserSettings.configure();
        closeWebDriver();
        lecture = Files.createTempFile("lecture-edit-", ".txt");
        Files.writeString(lecture, "Учебный материал по алгебре и SQL.\n".repeat(100), StandardCharsets.UTF_8);
        String username = "qa_edit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new RegisterPage().openPage().register(username, username + "@example.com", PASSWORD);
        registered = true;
        upload(ORIGINAL_NAME, ORIGINAL_SUBJECT, true);
        upload(OTHER_NAME, ORIGINAL_SUBJECT, false);
        upload(CATALOG_SEED, NEW_SUBJECT, true);
        original = materialsPage.material(ORIGINAL_NAME).snapshot();
        other = materialsPage.material(OTHER_NAME).snapshot();
        seed = materialsPage.material(CATALOG_SEED).snapshot();
    }

    @AfterEach
    void removeCreatedMaterials() throws IOException {
        try {
            if (registered) {
                materialsPage.openPage();
                while (!materialsPage.materialRows.isEmpty()) {
                    MaterialRow row = new MaterialRow(materialsPage.materialRows.first());
                    materialsPage.deleteIfPresent(row.title.getText());
                }
            }
        } finally {
            closeWebDriver();
            if (lecture != null) Files.deleteIfExists(lecture);
        }
    }

    @Test
    @DisplayName("TC-EDIT-01: изменение названия, остальные данные неизменны")
    void changeName() {
        String newName = "Алгебра — подготовка к экзамену";
        EditMaterialModal editor = openOriginalEditor();
        editor.nameInput.setValue(newName).shouldHave(value(newName));
        editor.saveButton.click();
        editor.modal.shouldNot(exist);
        assertMaterial(newName, original.renamed(newName));
        assertOtherMaterialsUnchanged();
    }

    @Test
    @DisplayName("TC-EDIT-02: изменение предмета, параметры файла недоступны для редактирования")
    void changeSubject() {
        EditMaterialModal editor = openOriginalEditor();
        editor.subjectInput.setValue(NEW_SUBJECT).shouldHave(value(NEW_SUBJECT));
        editor.saveButton.click();
        editor.modal.shouldNot(exist);
        assertMaterial(ORIGINAL_NAME, original.withSubject(NEW_SUBJECT));
        assertOtherMaterialsUnchanged();
    }

    static Stream<Arguments> invalidRequiredFields() {
        return Stream.of(
                Arguments.of("Пустое название", "", ORIGINAL_SUBJECT),
                Arguments.of("Название из трёх пробелов", "   ", ORIGINAL_SUBJECT),
                Arguments.of("Пустой предмет", ORIGINAL_NAME, ""),
                Arguments.of("Предмет из трёх пробелов", ORIGINAL_NAME, "   "));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequiredFields")
    @DisplayName("TC-EDIT-03: обязательные поля")
    void rejectBlankRequiredFields(String variant, String name, String subject) {
        EditMaterialModal editor = openOriginalEditor();
        editor.nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        if (!name.isEmpty()) editor.nameInput.setValue(name);
        editor.nameInput.shouldHave(domProperty("value", name));
        editor.subjectInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        if (!subject.isEmpty()) editor.subjectInput.setValue(subject);
        editor.subjectInput.shouldHave(domProperty("value", subject));
        AtomicInteger saves = new AtomicInteger();
        try (NetworkInterceptor ignored = failSaves(saves)) {
            editor.saveButton.click();
            editor.modal.shouldBe(visible);
            editor.errorMessage.shouldBe(visible).shouldHave(text("Заполните название и предмет"));
            editor.cancelButton.click();
            editor.modal.shouldNot(exist);
            assertMaterial(ORIGINAL_NAME, original);
            assertEquals(0, saves.get(), "Невалидная форма не должна отправлять PATCH");
        }
        refresh();
        materialsPage.shouldBeOpen();
        assertMaterial(ORIGINAL_NAME, original);
    }

    @Test
    @DisplayName("TC-EDIT-04: отмена не отправляет сохранение и не меняет материал")
    void cancelEditing() {
        EditMaterialModal editor = openOriginalEditor();
        editor.nameInput.setValue("Не сохранять изменения").shouldHave(value("Не сохранять изменения"));
        editor.subjectInput.setValue(NEW_SUBJECT).shouldHave(value(NEW_SUBJECT));
        AtomicInteger saves = new AtomicInteger();
        try (NetworkInterceptor ignored = failSaves(saves)) {
            editor.cancelButton.click();
            editor.modal.shouldNot(exist);
            assertMaterial(ORIGINAL_NAME, original);
            openOriginalEditor();
            editor.cancelButton.click();
            editor.modal.shouldNot(exist);
            assertEquals(0, saves.get(), "При отмене PATCH не должен отправляться");
        }
        assertOtherMaterialsUnchanged();
    }

    @Test
    @DisplayName("TC-EDIT-05: ошибка 500 сохраняет введённые данные и позволяет повторить сохранение")
    void retryAfterServerError() {
        String newName = "Алгебра после исправления";
        EditMaterialModal editor = openOriginalEditor();
        editor.nameInput.setValue(newName);
        editor.subjectInput.setValue(NEW_SUBJECT);
        AtomicInteger saves = new AtomicInteger();
        try (NetworkInterceptor ignored = failSaves(saves)) {
            editor.saveButton.click();
            editor.errorMessage.shouldBe(visible).shouldHave(text("Не удалось выполнить операцию"));
            editor.modal.shouldBe(visible);
            editor.nameInput.shouldHave(value(newName));
            editor.subjectInput.shouldHave(value(NEW_SUBJECT));
            assertMaterial(ORIGINAL_NAME, original);
            assertEquals(1, saves.get(), "Должен быть получен один ответ 500 на PATCH");
        }
        editor.saveButton.shouldBe(enabled).click();
        editor.modal.shouldNot(exist);
        Snapshot updated = original.renamed(newName).withSubject(NEW_SUBJECT);
        assertMaterial(newName, updated);
        refresh();
        materialsPage.shouldBeOpen();
        assertMaterial(newName, updated);
        assertOtherMaterialsUnchanged();
    }

    @Test
    @DisplayName("TC-EDIT-06: изменения сохраняются после обновления страницы")
    void persistAfterRefresh() {
        String newName = "Алгебра — итоговая версия";
        EditMaterialModal editor = openOriginalEditor();
        editor.nameInput.setValue(newName).shouldHave(value(newName));
        editor.subjectInput.setValue(NEW_SUBJECT).shouldHave(value(NEW_SUBJECT));
        editor.saveButton.click();
        editor.modal.shouldNot(exist);
        Snapshot updated = original.renamed(newName).withSubject(NEW_SUBJECT);
        assertMaterial(newName, updated);
        refresh();
        materialsPage.shouldBeOpen();
        assertMaterial(newName, updated);
        materialsPage.material(newName).editButton.click();
        editor.modal.shouldBe(visible);
        editor.nameInput.shouldHave(value(newName));
        editor.subjectInput.shouldHave(value(NEW_SUBJECT));
        editor.cancelButton.click();
        editor.modal.shouldNot(exist);
        assertOtherMaterialsUnchanged();
    }

    private EditMaterialModal openOriginalEditor() {
        materialsPage.material(ORIGINAL_NAME).editButton.click();
        EditMaterialModal editor = materialsPage.editModal;
        editor.modal.shouldBe(visible);
        editor.nameInput.shouldHave(value(ORIGINAL_NAME));
        editor.subjectInput.shouldHave(value(ORIGINAL_SUBJECT));
        editor.editableFields.shouldHave(size(2));
        return editor;
    }

    private void assertMaterial(String name, Snapshot expected) {
        MaterialRow row = materialsPage.material(name);
        row.title.shouldBe(visible).shouldHave(exactText(name));
        row.metadata.shouldHave(text(expected.subject()));
        assertEquals(expected, row.snapshot(), "Название, предмет, формат, размер и дата материала");
    }

    private void assertOtherMaterialsUnchanged() {
        assertMaterial(OTHER_NAME, other);
        assertMaterial(CATALOG_SEED, seed);
    }

    private void upload(String name, String subject, boolean createSubject) {
        UploadPage uploadPage = materialsPage.openPage().openUploadForm();
        uploadPage.fileInput.uploadFile(lecture.toFile());
        uploadPage.nameInput.setValue(name);
        if (createSubject) {
            uploadPage.subjectSelect.selectOption("Создать предмет");
            uploadPage.customSubjectInput.setValue(subject);
        } else {
            uploadPage.subjectSelect.selectOption(subject);
        }
        uploadPage.submitButton.click();
        uploadPage.shouldShowSuccess();
        uploadPage.successReturnLink.click();
        materialsPage.shouldBeOpen().material(name).title.shouldHave(exactText(name));
    }

    private NetworkInterceptor failSaves(AtomicInteger count) {
        Route fail = Route.matching(request -> PATCH == request.getMethod()
                        && request.getUri().matches("(?:https?://[^/]+)?/api/materials/[^/?]+(?:\\?.*)?"))
                .to(() -> request -> {
                    count.incrementAndGet();
                    return new HttpResponse().setStatus(500).addHeader("Content-Type", "application/json")
                            .setContent(Contents.utf8String("{\"error\":\"Test save error\"}"));
                });
        return new NetworkInterceptor(getWebDriver(), fail);
    }
}
