package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ElementsCollection;
import tech.genna.TY3.tests.ui.support.Name;

import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.open;

public final class MaterialsPage extends NamedPage {
    @Name("Заголовок страницы материалов")
    public final SelenideElement title = $x("//h1");
    @Name("Список материалов")
    public final SelenideElement list = $x("//section[@aria-label='Список материалов']");
    @Name("Загрузить материал")
    public final SelenideElement uploadButton = $x("//header//button[normalize-space()='Загрузить материал']");
    public final ElementsCollection materialRows = $$x("//section[@aria-label='Список материалов']//article");
    public final DeleteMaterialModal deleteModal = new DeleteMaterialModal();

    public MaterialsPage() { nameElements(); }

    public MaterialsPage openPage() {
        open("/materials");
        return shouldBeOpen();
    }

    public MaterialsPage shouldBeOpen() {
        title.shouldHave(exactText("Мои материалы"));
        list.shouldBe(visible);
        return this;
    }

    public UploadPage openUploadForm() {
        uploadButton.shouldHave(exactText("Загрузить материал")).click();
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
        material.$x(".//button[@aria-label='Удалить материал']").click();
        deleteModal.confirmButton.click();
        material.shouldNot(exist);
    }

    public SelenideElement row(String name) {
        return materialRows.findBy(text(name));
    }
}
