package tech.genna.TY3.tests.ui.pages;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

public final class RegisterPage {
    private static final String USERNAME = "#username";
    private static final String EMAIL = "#email";
    private static final String PASSWORD = "#password";
    private static final String CONFIRM_PASSWORD = "#confirmPassword";
    private static final String SUBMIT = "button[type=submit]";
    private static final String MATERIALS_NAVIGATION = "a[href='/materials']";

    public RegisterPage openPage() {
        open("/register");
        $(USERNAME).shouldBe(visible);
        return this;
    }

    public void register(String username, String email, String password) {
        $(USERNAME).setValue(username);
        $(EMAIL).setValue(email);
        $(PASSWORD).setValue(password);
        $(CONFIRM_PASSWORD).setValue(password);
        $(SUBMIT).click();
        $(MATERIALS_NAVIGATION).shouldBe(visible);
    }
}
