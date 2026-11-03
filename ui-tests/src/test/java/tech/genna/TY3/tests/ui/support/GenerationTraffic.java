package tech.genna.TY3.tests.ui.support;

import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.Filter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static org.openqa.selenium.remote.http.HttpMethod.POST;

/** Observes the real UI POST; never launches generation in place of the browser. */
public final class GenerationTraffic implements AutoCloseable {
    private final NetworkInterceptor interceptor;
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String authorization;
    private boolean observing = true;
    public volatile Map<String, Object> submitted;
    public volatile Map<String, Object> accepted;
    public final List<Map<String, Object>> submissions = new CopyOnWriteArrayList<>();
    public final List<Map<String, Object>> jobs = new CopyOnWriteArrayList<>();
    public final List<String> keys = new CopyOnWriteArrayList<>();
    public final List<String> statusRequests = new CopyOnWriteArrayList<>();
    public final List<Map<String, Object>> snapshots = new CopyOnWriteArrayList<>();

    public GenerationTraffic(String baseUrl) {
        this.baseUrl = baseUrl;
        interceptor = new NetworkInterceptor(getWebDriver(), (Filter) next -> request -> {
            if (authorization == null && URI.create(request.getUri()).getPath().startsWith("/api/")) authorization = request.getHeader("Authorization");
            if (request.getMethod() == org.openqa.selenium.remote.http.HttpMethod.GET && URI.create(request.getUri()).getPath().startsWith("/api/generation-jobs/")) statusRequests.add(URI.create(request.getUri()).getPath());
            boolean start = request.getMethod() == POST && URI.create(request.getUri()).getPath().equals("/api/quizzes");
            if (start) {
                authorization = request.getHeader("Authorization");
                submitted = json(Contents.string(request));
                submissions.add(submitted);
                keys.add(request.getHeader("Idempotency-Key"));
            }
            var response = next.execute(request);
            if (start && response.getStatus() == 202) { accepted = json(Contents.string(response)); jobs.add(accepted); }
            if (request.getMethod() == org.openqa.selenium.remote.http.HttpMethod.GET && URI.create(request.getUri()).getPath().startsWith("/api/generation-jobs/") && response.getStatus() == 200) recordSnapshot(json(Contents.string(response)));
            return response;
        });
    }

    public Map<String, Object> get(String path) {
        if (authorization == null) throw new AssertionError("UI did not send authenticated POST /api/quizzes");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", authorization).timeout(Duration.ofSeconds(20)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AssertionError("GET " + path + ": HTTP " + response.statusCode());
            var result = json(response.body());
            if (path.startsWith("/api/generation-jobs/")) recordSnapshot(result);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while checking generation");
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot read generation status: " + e.getClass().getSimpleName());
        }
    }

    public int send(String method, String path, Object body, boolean authenticated) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json").header("Idempotency-Key", java.util.UUID.randomUUID().toString());
            if (authenticated) {
                if (authorization == null) throw new AssertionError("No observed test-user session");
                builder.header("Authorization", authorization);
            }
            return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(new Json().toJson(body))).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted HTTP check"); }
        catch (java.io.IOException e) { throw new AssertionError("HTTP check failed: " + e.getClass().getSimpleName()); }
    }
    public Map<String, Object> replayLastSubmission() {
        if (submitted == null || authorization == null || keys.isEmpty()) throw new AssertionError("No observed generation to replay");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/quizzes"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .header("Authorization", authorization).header("Idempotency-Key", keys.getLast())
                    .POST(HttpRequest.BodyPublishers.ofString(new Json().toJson(submitted))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 202) throw new AssertionError("Idempotent replay: HTTP " + response.statusCode());
            return json(response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted idempotent replay"); }
        catch (java.io.IOException e) { throw new AssertionError("Cannot replay generation request"); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String body) { return new Json().toType(body, Map.class); }
    public void stopObserving() { if (observing) { interceptor.close(); observing = false; } }
    private synchronized void recordSnapshot(Map<String, Object> value) {
        if (snapshots.isEmpty() || !snapshots.getLast().equals(value)) snapshots.add(value);
    }
    @Override public void close() {
        stopObserving(); authorization = null;
        try {
            var directory = java.nio.file.Path.of(System.getProperty("ui.generationReportDir", "build/reports/generation"));
            java.nio.file.Files.createDirectories(directory);
            String id = jobs.isEmpty() ? java.util.UUID.randomUUID().toString() : jobs.getFirst().get("id").toString();
            var evidence = Map.of("baseUrl", baseUrl, "capturedAt", java.time.Instant.now().toString(),
                    "requests", submissions, "acceptedJobs", jobs, "statuses", snapshots);
            java.nio.file.Files.writeString(directory.resolve(id + ".json"), new Json().toJson(evidence));
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot save safe generation report", e); }
    }
}
