package tech.genna.TY3.tests.ui.pages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

public final class UploadPage {
    private static final String TITLE = "h1";
    private static final String FILE_INPUT = "input[type=file]";
    private static final String FILE_CARD = ".chosen-file";
    private static final String NAME = "#material-name";
    private static final String SUBJECT = "#subject";
    private static final String CUSTOM_SUBJECT = "#custom-subject";
    private static final String DESCRIPTION = "#description";
    private static final String TAG_INPUT = "#tag-input";
    private static final String ADD_TAG = ".tag-editor button";
    private static final String TAG = ".tag";
    private static final String SUBMIT = "form button[type=submit]";
    private static final String ALERT = "[role=alert]";
    private static final String SUCCESS = ".success";
    private static final String SUCCESS_TITLE = ".success h2";
    private static final String SUCCESS_RETURN = ".success a[href='/materials']";
    private static final String BREADCRUMB_RETURN = "nav.breadcrumbs a[href='/materials']";

    public UploadPage shouldBeOpen() {
        $(TITLE).shouldHave(exactText("Загрузка материала"));
        return this;
    }

    public UploadPage chooseFile(File file) {
        // Sending the path to the page's file input is Selenium's supported file chooser action.
        $(FILE_INPUT).uploadFile(file);
        return this;
    }

    public UploadPage shouldShowSelectedFile(File file, String format) throws IOException {
        String shownSize = String.format(Locale.US, "%.2f", Files.size(file.toPath()) / 1048576.0);
        $(FILE_CARD).shouldHave(text(file.getName()), text(format), text(shownSize + " МБ"));
        return this;
    }

    public UploadPage shouldHaveNoSelectedFile() {
        $$(FILE_CARD).shouldHave(size(0));
        return this;
    }

    public UploadPage name(String name) {
        $(NAME).setValue(name).shouldHave(value(name));
        return this;
    }

    public UploadPage subject(String subject) {
        $(SUBJECT).selectOption(subject);
        $(SUBJECT).shouldHave(value(subject));
        return this;
    }

    public UploadPage createSubject(String subject) {
        $(SUBJECT).selectOption("Создать предмет");
        $(CUSTOM_SUBJECT).setValue(subject).shouldHave(value(subject));
        return this;
    }

    public UploadPage description(String description) {
        $(DESCRIPTION).setValue(description).shouldHave(value(description));
        return this;
    }

    public UploadPage addTag(String tag) {
        $(TAG_INPUT).setValue(tag);
        $(ADD_TAG).click();
        $(TAG).shouldHave(text(tag));
        return this;
    }

    public UploadPage submit() {
        $(SUBMIT).click();
        return this;
    }

    public UploadPage shouldShowSuccess() {
        $(SUCCESS_TITLE).shouldHave(exactText("Материал сохранён на сервере"));
        $(SUCCESS).shouldHave(text("Идентификатор материала:"));
        return this;
    }

    public UploadPage shouldShowError(String message) {
        $(ALERT).shouldHave(text(message));
        return this;
    }

    public UploadPage shouldNotShowSuccess() {
        $(SUCCESS).shouldNot(exist);
        return this;
    }

    public MaterialsPage backToMaterials() {
        if ($(SUCCESS).exists()) $(SUCCESS_RETURN).click();
        else $(BREADCRUMB_RETURN).click();
        return new MaterialsPage().shouldBeOpen();
    }
}
