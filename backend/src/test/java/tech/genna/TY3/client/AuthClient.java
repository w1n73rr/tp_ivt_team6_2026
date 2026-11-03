package tech.genna.TY3.client;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import tech.genna.TY3.api.model.LoginRequest;
import tech.genna.TY3.api.model.RegisterRequest;
import java.util.Map;

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
                .body(Map.of("username", registerRequest.getUsername(), "email", registerRequest.getEmail(),
                        "password", registerRequest.getPassword()))
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
                .body(Map.of("username", loginRequest.getUsername(), "password", loginRequest.getPassword()))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .response();
    }
}
