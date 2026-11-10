package tech.genna.TY3.tests.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import tech.genna.TY3.tests.ui.pages.DeleteMaterialModal;
import tech.genna.TY3.tests.ui.pages.MaterialRow;
import tech.genna.TY3.tests.ui.pages.MaterialRow.Snapshot;
import tech.genna.TY3.tests.ui.pages.MaterialsPage;
import tech.genna.TY3.tests.ui.pages.RegisterPage;
import tech.genna.TY3.tests.ui.pages.UploadPage;
import tech.genna.TY3.tests.ui.support.BrowserSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codeborne.selenide.CollectionCondition.exactTexts;
import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openqa.selenium.remote.http.HttpMethod.DELETE;

public class MaterialDeleteUiTest {
    private static final String M1 = "Алгебра";
    private static final String M2 = "Геометрия";
    private static final String M3 = "Тригонометрия";
    private static final String SUBJECT = "Математика";
    private final MaterialsPage materialsPage = new MaterialsPage();
    private final Map<String, Snapshot> originals = new LinkedHashMap<>();
    private Path lecture;
    private boolean registered;

    @BeforeEach
    void prepareIndependentMaterials() throws IOException {
        BrowserSettings.configure();
        closeWebDriver();
        lecture = Files.createTempFile("lecture-delete-", ".txt");
        Files.writeString(lecture, "Учебный материал: алгебра, геометрия и тригонометрия.\n".repeat(100), StandardCharsets.UTF_8);
        String username = "qa_delete_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new RegisterPage().openPage().register(username, username + "@example.com", "QaDelete2026!Local");
        registered = true;
        upload(M1, true);
        upload(M2, false);
        upload(M3, false);
        shouldHaveCount(3);
        for (String name : new String[]{M1, M2, M3}) {
            originals.put(name, materialsPage.material(name).snapshot());
        }
    }

    @AfterEach
    void removeRemainingTestMaterials() throws IOException {
        try {
            if (registered) {
                materialsPage.openPage();
                for (String name : new String[]{M1, M2, M3}) materialsPage.deleteIfPresent(name);
                shouldHaveCount(0);
            }
        } finally {
            closeWebDriver();
            if (lecture != null) Files.deleteIfExists(lecture);
        }
    }

    @Test
    @DisplayName("TC-DEL-01: подтверждение указывает материал и не удаляет его без выбора действия")
    void confirmationDialog() {
        AtomicInteger deletes = new AtomicInteger();
        try (NetworkInterceptor ignored = failDeletes(deletes)) {
            DeleteMaterialModal dialog = openDeleteDialog();
            assertAllMaterialsUnchanged();
            assertEquals(0, deletes.get(), "Открытие окна не должно отправлять DELETE");
            dialog.cancelButton.click();
            dialog.modal.shouldNot(exist);
            refresh();
            materialsPage.shouldBeOpen();
            assertAllMaterialsUnchanged();
            assertEquals(0, deletes.get(), "Без подтверждения DELETE не отправляется");
        }
    }

    @Test
    @DisplayName("TC-DEL-02: удаляется только выбранный материал, счётчик равен двум")
    void successfulDelete() {
        DeleteMaterialModal dialog = openDeleteDialog();
        dialog.confirmButton.click();
        dialog.modal.shouldNot(exist);
        materialsPage.notice.shouldBe(visible).shouldHave(exactText("Удалено материалов: 1."));
        assertOnlyTargetDeleted();
    }

    @Test
    @DisplayName("TC-DEL-03: отмена не отправляет DELETE и сохраняет все материалы")
    void cancelDelete() {
        AtomicInteger deletes = new AtomicInteger();
        try (NetworkInterceptor ignored = failDeletes(deletes)) {
            DeleteMaterialModal dialog = openDeleteDialog();
            dialog.cancelButton.click();
            dialog.modal.shouldNot(exist);
            assertAllMaterialsUnchanged();
            assertEquals(0, deletes.get(), "При отмене DELETE не должен отправляться");
            refresh();
            materialsPage.shouldBeOpen();
            assertAllMaterialsUnchanged();
            assertEquals(0, deletes.get(), "Отмена не должна выполнять отложенное удаление");
        }
    }

    @Test
    @DisplayName("TC-DEL-04: при ошибке 500 материал остаётся, показана ошибка и счётчик равен трём")
    void serverError() {
        AtomicInteger deletes = new AtomicInteger();
        try (NetworkInterceptor ignored = failDeletes(deletes)) {
            DeleteMaterialModal dialog = openDeleteDialog();
            dialog.confirmButton.click();
            dialog.modal.shouldNot(exist);
            materialsPage.notice.shouldBe(visible).shouldHave(
                    text("Удалено материалов: 0."), text("Не удалено: 1."),
                    text("Не удалось выполнить операцию. Повторите попытку."));
            assertEquals(1, deletes.get(), "Один запрос удаления должен получить ответ 500");
            assertAllMaterialsUnchanged();
        }
        refresh();
        materialsPage.shouldBeOpen();
        assertAllMaterialsUnchanged();
    }

    @Test
    @DisplayName("TC-DEL-05: удалённый материал не появляется после обновления страницы")
    void deletionPersistsAfterRefresh() {
        DeleteMaterialModal dialog = openDeleteDialog();
        dialog.confirmButton.click();
        dialog.modal.shouldNot(exist);
        materialsPage.notice.shouldBe(visible).shouldHave(exactText("Удалено материалов: 1."));
        assertOnlyTargetDeleted();
        refresh();
        materialsPage.shouldBeOpen();
        assertOnlyTargetDeleted();
    }

    private DeleteMaterialModal openDeleteDialog() {
        materialsPage.material(M1).deleteButton.shouldBe(enabled).click();
        DeleteMaterialModal dialog = materialsPage.deleteModal;
        dialog.modal.shouldBe(visible);
        dialog.title.shouldHave(exactText("Удалить материал?"));
        dialog.warning.shouldHave(text("Файл и его данные будут удалены"), text("Это действие нельзя отменить"));
        dialog.materialNames.shouldHave(exactTexts(M1));
        dialog.confirmButton.shouldBe(visible, enabled);
        dialog.cancelButton.shouldBe(visible, enabled);
        return dialog;
    }

    private void assertAllMaterialsUnchanged() {
        shouldHaveCount(3);
        for (String name : originals.keySet()) assertUnchanged(name);
    }

    private void assertOnlyTargetDeleted() {
        materialsPage.material(M1).row.shouldNot(exist);
        shouldHaveCount(2);
        assertUnchanged(M2);
        assertUnchanged(M3);
    }

    private void shouldHaveCount(int count) {
        materialsPage.materialRows.shouldHave(size(count));
        materialsPage.foundCount.shouldHave(exactText("Найдено материалов: " + count));
    }

    private void assertUnchanged(String name) {
        MaterialRow row = materialsPage.material(name);
        row.title.shouldBe(visible).shouldHave(exactText(name));
        assertEquals(originals.get(name), row.snapshot(), "Данные материала должны сохраниться: " + name);
    }

    private void upload(String name, boolean createSubject) {
        UploadPage page = materialsPage.openPage().openUploadForm();
        page.fileInput.uploadFile(lecture.toFile());
        page.nameInput.setValue(name);
        if (createSubject) {
            page.subjectSelect.selectOption("Создать предмет");
            page.customSubjectInput.setValue(SUBJECT);
        } else {
            page.subjectSelect.selectOption(SUBJECT);
        }
        page.submitButton.click();
        page.shouldShowSuccess();
        page.successReturnLink.click();
        materialsPage.shouldBeOpen().material(name).title.shouldHave(exactText(name));
    }

    private NetworkInterceptor failDeletes(AtomicInteger count) {
        Route fail = Route.matching(request -> DELETE == request.getMethod()
                        && request.getUri().matches("(?:https?://[^/]+)?/api/materials/[^/?]+(?:\\?.*)?"))
                .to(() -> request -> {
                    count.incrementAndGet();
                    return new HttpResponse().setStatus(500).addHeader("Content-Type", "application/json")
                            .setContent(Contents.utf8String("{\"error\":\"Test delete error\"}"));
                });
        return new NetworkInterceptor(getWebDriver(), fail);
    }
}
