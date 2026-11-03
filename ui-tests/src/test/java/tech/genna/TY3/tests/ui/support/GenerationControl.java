package tech.genna.TY3.tests.ui.support;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.openqa.selenium.json.Json;

/** Loopback-only controller of the test-classpath backend, never a production endpoint. */
public final class GenerationControl {
    private GenerationControl() {}
    public static Map<String, Object> mode(String mode) { return read("/control?mode=" + mode); }
    public static Map<String, Object> stats() { return read("/control"); }
    public static Map<String, Object> result(String jobId) { return read("/result?" + java.util.UUID.fromString(jobId)); }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create(System.getProperty("ui.controlUrl", "http://localhost:18084") + path)).timeout(Duration.ofSeconds(10)).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AssertionError("Controlled environment: HTTP " + response.statusCode());
            return new Json().toType(response.body(), Map.class);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted controlled environment"); }
        catch (java.io.IOException e) { throw new AssertionError("Start generationUiServer and controlled frontend; control is unavailable"); }
    }
}
