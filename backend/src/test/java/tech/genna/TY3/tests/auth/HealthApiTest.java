package tech.genna.TY3.tests.auth;

import org.junit.jupiter.api.Test;
import tech.genna.TY3.tests.base.BaseTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HealthApiTest extends BaseTest {
    @Test
    void healthIsAvailableWithoutCredentialsAndHidesComponents() throws Exception {
        var response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        var health = JsonMapper.builder().build().readTree(response.body());
        assertEquals("UP", health.get("status").asText());
        assertFalse(health.has("components"));
        assertFalse(health.has("details"));
    }

    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        assertEquals(401, get("/actuator/env").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
