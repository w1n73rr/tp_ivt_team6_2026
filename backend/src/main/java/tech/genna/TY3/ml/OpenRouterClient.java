package tech.genna.TY3.ml;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static tech.genna.TY3.ml.GenerationFailure.Code.*;

/** Chat Completions SSE adapter; no paid fallback, automatic retry, raw body logging or UI transport. */
public final class OpenRouterClient implements ModelClient {
    // SSE repeats envelope metadata per chunk; wire size is much larger than answer text.
    private static final int MAX_STREAM_CHARACTERS = 16 * 1024 * 1024;
    private final String key, model;
    private final URI endpoint;
    private final Duration timeout;
    private final int maxTokens;
    private final boolean reasoning;
    private final HttpClient http;

    public OpenRouterClient(String key, String model, Duration timeout, int maxTokens) {
        this(key, model, timeout, maxTokens, false);
    }

    public OpenRouterClient(String key, String model, Duration timeout, int maxTokens, boolean reasoning) {
        this(key, model, URI.create("https://openrouter.ai/api/v1/chat/completions"), timeout, maxTokens, reasoning);
    }

    // Package-private test seam. Public production constructor fixes the trusted HTTPS host.
    OpenRouterClient(String key, String model, URI endpoint, Duration timeout, int maxTokens) {
        this(key, model, endpoint, timeout, maxTokens, false);
    }

    private OpenRouterClient(String key, String model, URI endpoint, Duration timeout, int maxTokens, boolean reasoning) {
        this.key = key;
        if (model == null || !model.endsWith(":free") || timeout == null
                || timeout.isNegative() || timeout.isZero() || maxTokens < 1 || maxTokens > 16000) {
            throw new IllegalArgumentException("Invalid ML configuration; only explicit :free models allowed");
        }
        this.model = model;
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.maxTokens = maxTokens;
        this.reasoning = reasoning;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public String complete(String system, String user, Runnable firstContent) {
        if (key == null || key.isBlank() || !key.matches("[A-Za-z0-9_-]+")) {
            throw new GenerationFailure(NOT_CONFIGURED);
        }
        String body = Json.write(Map.of("model", model, "stream", true, "temperature", 0.2,
                "max_tokens", maxTokens, "response_format", Map.of("type", "json_object"),
                "provider", Map.of("require_parameters", true), "reasoning", Map.of("enabled", reasoning), "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user))));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        // Covers response headers AND the complete streaming body, including silent stalled streams.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> {
                try {
                    HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream stream = response.body()) {
                        int status = response.statusCode();
                        if (status != 200) throw new GenerationFailure(status == 429 ? RATE_LIMITED
                                : status == 401 || status == 403 ? NOT_CONFIGURED : MODEL_UNAVAILABLE);
                        if (!response.headers().firstValue("Content-Type").orElse("")
                                .toLowerCase(Locale.ROOT).contains("text/event-stream")) {
                            throw new GenerationFailure(INVALID_MODEL_OUTPUT);
                        }
                        return readStream(stream, firstContent);
                    }
                } catch (HttpTimeoutException ex) { throw new GenerationFailure(MODEL_TIMEOUT); }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); throw new GenerationFailure(CANCELLED);
                } catch (IOException ex) { throw new GenerationFailure(MODEL_UNAVAILABLE); }
            });
            try { return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (TimeoutException ex) { future.cancel(true); throw new GenerationFailure(MODEL_TIMEOUT); }
            catch (InterruptedException ex) {
                future.cancel(true); Thread.currentThread().interrupt(); throw new GenerationFailure(CANCELLED);
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof GenerationFailure failure) throw failure;
                throw new GenerationFailure(MODEL_UNAVAILABLE);
            }
        }
    }

    static String readStream(InputStream stream, Runnable firstContent) throws IOException {
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder line = new StringBuilder(), event = new StringBuilder(), answer = new StringBuilder();
        boolean[] first = {true}, stopped = {false};
        int total = 0, value;
        while ((value = reader.read()) != -1) {
            if (++total > MAX_STREAM_CHARACTERS) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
            if (value != '\n') {
                line.append((char) value);
                if (line.length() > 262144) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
                continue;
            }
            String s = line.toString(); line.setLength(0);
            if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
            if (s.isEmpty()) {
                if (!event.isEmpty()) {
                    String data = event.toString(); event.setLength(0);
                    if (data.equals("[DONE]")) {
                        if (!stopped[0] || answer.isEmpty()) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
                        return answer.toString();
                    }
                    consume(data, answer, firstContent, first, stopped);
                }
            } else if (s.startsWith("data:")) {
                String data = s.substring(5);
                if (data.startsWith(" ")) data = data.substring(1);
                if (!event.isEmpty()) event.append('\n');
                event.append(data);
                if (event.length() > 262144) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
            } // Comments, event/id/retry fields are SSE framing, not model JSON.
        }
        throw new GenerationFailure(INVALID_MODEL_OUTPUT); // Includes missing [DONE] / truncated transport.
    }

    private static void consume(String data, StringBuilder answer, Runnable notify,
                                boolean[] first, boolean[] stopped) {
        Map<?, ?> chunk = Json.read(data);
        if (chunk.containsKey("error")) throw new GenerationFailure(MODEL_UNAVAILABLE);
        Object choices = chunk.get("choices");
        if (!(choices instanceof List<?> list)) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
        if (list.isEmpty()) return; // Accounting frame.
        if (list.size() != 1 || !(list.getFirst() instanceof Map<?, ?> choice)) {
            throw new GenerationFailure(INVALID_MODEL_OUTPUT);
        }
        Object delta = choice.get("delta");
        if (delta instanceof Map<?, ?> d && d.get("content") instanceof String content && !content.isEmpty()) {
            if (stopped[0]) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
            if (first[0]) { first[0] = false; notify.run(); }
            answer.append(content);
            if (answer.length() > 100000) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
        }
        Object finish = choice.get("finish_reason");
        if (finish != null) {
            if ("length".equals(finish)) throw new GenerationFailure(MODEL_OUTPUT_LIMIT);
            if ("content_filter".equals(finish)) throw new GenerationFailure(MODEL_REFUSED);
            if (!"stop".equals(finish)) throw new GenerationFailure(INVALID_MODEL_OUTPUT);
            stopped[0] = true; // Repeated stop in a final usage chunk is valid.
        }
    }
}
