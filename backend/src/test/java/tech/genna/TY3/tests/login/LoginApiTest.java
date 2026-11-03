package tech.genna.TY3.tests.login;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.genna.TY3.client.AuthClient;
import tech.genna.TY3.api.model.LoginRequest;
import tech.genna.TY3.api.model.RegisterRequest;
import tech.genna.TY3.tests.base.BaseTest;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;

public class LoginApiTest extends BaseTest {

    @Test
    @DisplayName("Успешная авторизация с корректными данными")
    void testSuccessfulLogin() {
        AuthClient authClient = new AuthClient(port);

        // 1. Подготовка данных: генерируем уникального пользователя
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "login_user_" + uniqueSuffix;
        String email = "login_user_" + uniqueSuffix + "@mail.com";
        String password = "password123";

        // 2. Предварительная регистрация пользователя
        RegisterRequest registerRequest = new RegisterRequest(username, email, password);
        authClient.register(registerRequest, 200);

        // 3. Выполнение входа (авторизация)
        LoginRequest loginRequest = new LoginRequest(username, password);
        Response loginResponse = authClient.login(loginRequest, 200);

        // 4. Проверка ответа (наличие JWT-токена в ответе)
        loginResponse.then()
                .body("token", notNullValue());
    }

    @Test
    @DisplayName("Ошибка авторизации при передаче неверного пароля")
    void testLoginWithInvalidPassword() {
        AuthClient authClient = new AuthClient(port);

        // 1. Регистрация пользователя
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "login_fail_" + uniqueSuffix;
        String email = "login_fail_" + uniqueSuffix + "@mail.com";
        String password = "correctPassword123";

        authClient.register(new RegisterRequest(username, email, password), 200);

        // 2. Попытка входа с неверным паролем (ожидаем статус 401 или 400 в зависимости от логики)
        LoginRequest invalidLoginRequest = new LoginRequest(username, "wrongPassword");
        authClient.login(invalidLoginRequest, 401);
    }
}
