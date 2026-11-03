package tech.genna.TY3.tests.registration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.genna.TY3.client.AuthClient;
import tech.genna.TY3.api.model.RegisterRequest;
import tech.genna.TY3.tests.base.BaseTest;

import java.util.UUID;

public class RegisterApiTest extends BaseTest {

    @Test
    @DisplayName("Успешная регистрация нового пользователя")
    void testSuccessfulRegistration() {
        AuthClient authClient = new AuthClient(port);

        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "user" + uniqueSuffix;
        String email = "user" + uniqueSuffix + "@mail.com";
        String password = "password123";

        RegisterRequest registerRequest = new RegisterRequest(username, email, password);

        authClient.register(registerRequest, 200);
    }
}
