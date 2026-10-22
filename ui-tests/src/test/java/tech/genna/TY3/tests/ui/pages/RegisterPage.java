package tech.genna.TY3.tests.ui.pages;

import com.codeborne.selenide.SelenideElement;
import tech.genna.TY3.tests.ui.support.Name;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.open;

public final class RegisterPage extends NamedPage {
    @Name("Имя пользователя")
    public final SelenideElement usernameInput = $x("//*[@id='username']");
    @Name("Электронная почта")
    public final SelenideElement emailInput = $x("//*[@id='email']");
    @Name("Пароль")
    public final SelenideElement passwordInput = $x("//*[@id='password']");
    @Name("Подтверждение пароля")
    public final SelenideElement confirmPasswordInput = $x("//*[@id='confirmPassword']");
    @Name("Отправить форму")
    public final SelenideElement submitButton = $x("//form//button[@type='submit']");
    @Name("Мои материалы")
    public final SelenideElement materialsLink = $x("//a[@href='/materials']");

    public RegisterPage() {
        nameElements();
    }

    public RegisterPage openPage() {
        open("/register");
        usernameInput.shouldBe(visible);
        return this;
    }

    public void register(String username, String email, String password) {
        usernameInput.setValue(username);
        emailInput.setValue(email);
        passwordInput.setValue(password);
        confirmPasswordInput.setValue(password);
        submitButton.click();
        materialsLink.shouldBe(visible);
    }
}
