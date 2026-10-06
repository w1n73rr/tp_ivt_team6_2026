package tech.genna.TY3.client;

import io.restassured.response.Response;
import tech.genna.TY3.config.TestConfig;
import tech.genna.TY3.dto.auth.LoginRequest;
import tech.genna.TY3.dto.auth.RegisterRequest;

import static io.restassured.RestAssured.given;

public class AuthClient {

    public AuthClient(int port) {
        TestConfig.setupRequestSpecification(port);
    }

    public Response register(RegisterRequest request, int expectedStatus) {
        return given()
                .body(request)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(expectedStatus)
                .extract()
                .response();
    }

    public Response login(LoginRequest request, int expectedStatus) {
        return given()
                .body(request)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(expectedStatus)
                .extract()
                .response();
    }
}