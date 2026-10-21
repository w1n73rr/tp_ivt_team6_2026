package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;

public final class MaterialsPage {
    private static final String TITLE = "h1";
    private static final String LIST = "section[aria-label='Список материалов']";
    private static final String UPLOAD_BUTTON = "header button";
    private static final String MATERIAL_ROWS = ".list article";
    private static final String DELETE_BUTTON = "button[aria-label='Удалить материал']";
    private static final String CONFIRM_DELETE_BUTTONS = "[role=dialog] button";

    public MaterialsPage openPage() {
        open("/materials");
        return shouldBeOpen();
    }

    public MaterialsPage shouldBeOpen() {
        $(TITLE).shouldHave(exactText("Мои материалы"));
        $(LIST).shouldBe(visible);
        return this;
    }

    public UploadPage openUploadForm() {
        $(UPLOAD_BUTTON).shouldHave(exactText("Загрузить материал")).click();
        return new UploadPage().shouldBeOpen();
    }

    public MaterialsPage shouldContain(String name, String subject, String format) {
        row(name).shouldBe(visible).shouldHave(text(subject), text(format));
        return this;
    }

    public MaterialsPage shouldContain(String name) {
        row(name).shouldBe(visible);
        return this;
    }

    public MaterialsPage shouldNotContain(String name) {
        row(name).shouldNot(exist);
        return this;
    }

    public void deleteIfPresent(String name) {
        SelenideElement material = row(name);
        if (!material.exists()) return;
        material.$(DELETE_BUTTON).click();
        $$(CONFIRM_DELETE_BUTTONS).findBy(exactText("Удалить")).click();
        material.shouldNot(exist);
    }

    private SelenideElement row(String name) {
        return $$(MATERIAL_ROWS).findBy(text(name));
    }
}
