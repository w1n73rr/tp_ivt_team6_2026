package tech.genna.TY3.tests.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.genna.TY3.client.AuthClient;
import tech.genna.TY3.dto.auth.LoginRequest;
import tech.genna.TY3.tests.base.BaseTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AuthApiTest extends BaseTest {

    @Test
    @DisplayName("Проверка инфраструктуры автотестов: отправка невалидного запроса на авторизацию")
    void testAuthInfrastructureWithInvalidCredentials() {
        AuthClient authClient = new AuthClient(port);
        LoginRequest invalidRequest = new LoginRequest("non_existent_user", "wrong_password");

        var response = authClient.login(invalidRequest, 401);

        assertNotNull(response, "Ответ от API не должен быть null");
    }
}