package tech.genna.TY3.client;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import tech.genna.TY3.dto.auth.LoginRequest;
import tech.genna.TY3.dto.auth.RegisterRequest;

import static io.restassured.RestAssured.given;

public class AuthClient {

    private final int port;

    public AuthClient(int port) {
        this.port = port;
    }

    public Response register(RegisterRequest registerRequest, int expectedStatusCode) {
        return given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(registerRequest)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .response();
    }

    public Response login(LoginRequest loginRequest, int expectedStatusCode) {
        return given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .response();
    }
}