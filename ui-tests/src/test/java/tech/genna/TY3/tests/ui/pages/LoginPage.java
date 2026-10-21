package tech.genna.TY3.tests.ui.pages;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

public final class LoginPage {
    private static final String USERNAME = "#username";
    private static final String PASSWORD = "#password";
    private static final String SUBMIT = "button[type=submit]";
    private static final String MATERIALS_NAVIGATION = "a[href='/materials']";

    public LoginPage openPage() {
        open("/login");
        $(USERNAME).shouldBe(visible);
        return this;
    }

    public void signIn(String username, String password) {
        $(USERNAME).setValue(username);
        $(PASSWORD).setValue(password);
        $(SUBMIT).click();
        $(MATERIALS_NAVIGATION).shouldBe(visible);
    }
}
