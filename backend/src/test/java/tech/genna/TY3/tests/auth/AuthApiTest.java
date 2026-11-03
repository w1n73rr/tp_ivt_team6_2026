package tech.genna.TY3.tests.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.genna.TY3.client.AuthClient;
import tech.genna.TY3.api.model.LoginRequest;
import tech.genna.TY3.tests.base.BaseTest;

public class AuthApiTest extends BaseTest {

    @Test
    @DisplayName("Проверка работы тестовой инфраструктуры при передаче невалидных данных авторизации")
    void testAuthInfrastructureSetup() {
        AuthClient authClient = new AuthClient(port);
        LoginRequest invalidRequest = new LoginRequest("invalid_user", "wrong_password");
        authClient.login(invalidRequest, 401);
    }
}
