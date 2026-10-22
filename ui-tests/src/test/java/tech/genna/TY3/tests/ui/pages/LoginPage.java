package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.open;

public final class LoginPage extends NamedPage {
    @Name("Имя пользователя")
    public final SelenideElement usernameInput = $x("//*[@id='username']");
    @Name("Пароль")
    public final SelenideElement passwordInput = $x("//*[@id='password']");
    @Name("Отправить форму")
    public final SelenideElement submitButton = $x("//form//button[@type='submit']");
    @Name("Мои материалы")
    public final SelenideElement materialsLink = $x("//a[@href='/materials']");

    public LoginPage() {
        nameElements();
    }

    public LoginPage openPage() {
        open("/login");
        usernameInput.shouldBe(visible);
        return this;
    }

    public void signIn(String username, String password) {
        usernameInput.setValue(username);
        passwordInput.setValue(password);
        submitButton.click();
        materialsLink.shouldBe(visible);
    }
}
