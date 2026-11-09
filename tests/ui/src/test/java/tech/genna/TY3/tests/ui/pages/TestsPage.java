package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ElementsCollection;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Condition.*;

public final class TestsPage extends NamedPage {
    @Name("Заголовок списка тестов") public final SelenideElement title = $x("//h1");
    @Name("Поиск теста") public final SelenideElement searchInput = $x("//input[@placeholder='Поиск по названию теста']");
    @Name("Демонстрационный список") public final SelenideElement demoNotice = $x("//details[@class='demo']");
    public final ElementsCollection rows = $$x("//table/tbody/tr");
    public TestsPage() { nameElements(); }
    public TestsPage openPage() {
        open("/tests");
        title.shouldHave(exactText("Тесты"));
        return this;
    }
}
