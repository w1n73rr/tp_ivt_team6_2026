package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Selenide.$x;


public final class UploadPage extends NamedPage {
    @Name("Заголовок страницы")
    public final SelenideElement title = $x("//h1");
    @Name("Выбор файла")
    public final SelenideElement fileInput = $x("//input[@type='file']");
    @Name("Карточка выбранного файла")
    public final SelenideElement fileCard = $x("//*[@class='chosen-file']");
    @Name("Название материала")
    public final SelenideElement nameInput = $x("//*[@id='material-name']");
    @Name("Предмет")
    public final SelenideElement subjectSelect = $x("//*[@id='subject']");
    @Name("Новый предмет")
    public final SelenideElement customSubjectInput = $x("//*[@id='custom-subject']");
    @Name("Описание материала")
    public final SelenideElement descriptionInput = $x("//*[@id='description']");
    @Name("Тег")
    public final SelenideElement tagInput = $x("//*[@id='tag-input']");
    @Name("Добавить тег")
    public final SelenideElement addTagButton = $x("//*[@class='tag-editor']//button");
    @Name("Добавленный тег")
    public final SelenideElement tag = $x("//*[contains(concat(' ',normalize-space(@class),' '),' tag ')]");
    @Name("Отправить форму")
    public final SelenideElement submitButton = $x("//form//button[@type='submit']");
    @Name("Сообщение об ошибке")
    public final SelenideElement errorMessage = $x("//*[@role='alert']");
    @Name("Подтверждение загрузки")
    public final SelenideElement successPanel = $x("//*[@class='success']");
    @Name("Заголовок подтверждения")
    public final SelenideElement successTitle = $x("//*[@class='success']//h2");
    @Name("К материалам после загрузки")
    public final SelenideElement successReturnLink = $x("//*[@class='success']//a[@href='/materials']");
    @Name("Вернуться к материалам")
    public final SelenideElement materialsLink = $x("//nav[contains(@class,'breadcrumbs')]//a[@href='/materials']");

    public UploadPage() {
        nameElements();
    }

    public UploadPage shouldBeOpen() {
        title.shouldHave(exactText("Загрузка материала"));
        return this;
    }

    public UploadPage chooseFile(File file) {
        fileInput.uploadFile(file);
        return this;
    }

    public UploadPage shouldShowSelectedFile(File file, String format) throws IOException {
        String shownSize = String.format(Locale.US, "%.2f", Files.size(file.toPath()) / 1048576.0);
        fileCard.shouldHave(text(file.getName()), text(format), text(shownSize + " МБ"));
        return this;
    }

    public UploadPage shouldHaveNoSelectedFile() {
        fileCard.shouldNot(exist);
        return this;
    }

    public UploadPage name(String name) {
        nameInput.setValue(name).shouldHave(value(name));
        return this;
    }

    public UploadPage subject(String subject) {
        subjectSelect.selectOption(subject);
        subjectSelect.shouldHave(value(subject));
        return this;
    }

    public UploadPage createSubject(String subject) {
        subjectSelect.selectOption("Создать предмет");
        customSubjectInput.setValue(subject).shouldHave(value(subject));
        return this;
    }

    public UploadPage description(String description) {
        descriptionInput.setValue(description).shouldHave(value(description));
        return this;
    }

    public UploadPage addTag(String tag) {
        tagInput.setValue(tag);
        addTagButton.click();
        this.tag.shouldHave(text(tag));
        return this;
    }

    public UploadPage submit() {
        submitButton.click();
        return this;
    }

    public UploadPage shouldShowSuccess() {
        successTitle.shouldHave(exactText("Материал сохранён на сервере"));
        successPanel.shouldHave(text("Идентификатор материала:"));
        return this;
    }

    public UploadPage shouldShowError(String message) {
        errorMessage.shouldHave(text(message));
        return this;
    }

    public UploadPage shouldNotShowSuccess() {
        successPanel.shouldNot(exist);
        return this;
    }

    public MaterialsPage backToMaterials() {
        if (successPanel.exists()) successReturnLink.click();
        else materialsLink.click();
        return new MaterialsPage().shouldBeOpen();
    }
}
