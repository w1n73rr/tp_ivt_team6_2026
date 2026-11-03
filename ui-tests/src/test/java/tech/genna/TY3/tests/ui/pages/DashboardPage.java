package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;
import static com.codeborne.selenide.Selenide.$x;

public final class DashboardPage extends NamedPage {
    @Name("Приветствие на главной странице")
    public final SelenideElement title = $x("//h1");
    @Name("Переход в Мои материалы")
    public final SelenideElement materialsLink = $x("//a[@href='/materials']");

    public DashboardPage() { nameElements(); }
}
