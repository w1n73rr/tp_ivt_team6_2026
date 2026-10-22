package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ElementsCollection;
import tech.genna.TY3.tests.ui.support.Name;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Condition.match;
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
    @Name("Количество найденных материалов")
    public final SelenideElement foundCount = list.$x(".//div[@class='list-head']/span");
    @Name("Результат операции с материалами")
    public final SelenideElement notice = $x("//app-materials-shell//p[@role='status']");
    @Name("Поиск по названию")
    public final SelenideElement searchInput = $x("//input[@aria-label='Поиск по названию']");
    @Name("Фильтр формата")
    public final SelenideElement formatSelect = $x("//select[@aria-label='Формат файла']");
    @Name("Пустой список материалов")
    public final SelenideElement emptyState = list.$x(".//div[@role='status']");
    @Name("Ошибка загрузки списка")
    public final SelenideElement listError = $x("//app-materials-shell//div[@role='alert']");
    @Name("Повторить загрузку списка")
    public final SelenideElement retryButton = listError.$x(".//button[normalize-space()='Повторить']");
    @Name("Выйти из аккаунта")
    public final SelenideElement logoutButton = $x("//app-materials-shell//button[normalize-space()='Выйти']");
    @Name("Текущий пользователь")
    public final SelenideElement accountName = $x("//app-materials-shell//div[@class='account']//b");
    public final ElementsCollection materialTitles = list.$$x(".//article//h2");
    public final ElementsCollection materialRows = $$x("//section[@aria-label='Список материалов']//article");
    public final EditMaterialModal editModal = new EditMaterialModal();
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

    public MaterialRow material(String name) {
        return new MaterialRow(row(name));
    }

    public SelenideElement row(String name) {
        return materialRows.findBy(match("Материал с названием «" + name + "»",
                element -> element.findElement(By.tagName("h2")).getText().equals(name)));
    }
}
