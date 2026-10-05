package tech.genna.TY3.client;

import io.restassured.response.Response;
import tech.genna.TY3.config.TestConfig;
import tech.genna.TY3.dto.auth.LoginRequest;
import tech.genna.TY3.dto.auth.RegisterRequest;

import static io.restassured.RestAssured.given;

public class AuthClient {

    private final int port;

    public AuthClient(int port) {
        this.port = port;
    }

    public Response login(LoginRequest request, int expectedStatus) {
        return given()
                .spec(TestConfig.requestSpec(port))
                .body(request)
                .when()
                .post("/api/auth/login")
                .then()
                .spec(TestConfig.responseSpec(expectedStatus))
                .extract()
                .response();
    }

    public Response register(RegisterRequest request, int expectedStatus) {
        return given()
                .spec(TestConfig.requestSpec(port))
                .body(request)
                .when()
                .post("/api/auth/register")
                .then()
                .spec(TestConfig.responseSpec(expectedStatus))
                .extract()
                .response();
    }
}