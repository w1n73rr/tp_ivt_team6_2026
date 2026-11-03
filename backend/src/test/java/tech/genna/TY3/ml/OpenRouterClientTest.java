package tech.genna.TY3.ml;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterClientTest {
    static String event(Object o) { return "data: " + Json.write(o) + "\n\n"; }
    static String chunk(String content, String finish) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("delta", content == null ? Map.of("reasoning", "not answer") : Map.of("content", content));
        choice.put("finish_reason", finish);
        return event(Map.of("choices", List.of(choice)));
    }
    static InputStream input(String s) { return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)); }

    @Test void parsesKeepalivesMultilineDataUnicodeAndUsageFrames() throws Exception {
        AtomicInteger first = new AtomicInteger();
        String data = ": OPENROUTER PROCESSING\r\n\r\n" + chunk(null, null)
                + "data: {\"choices\":\n" + "data: [{\"delta\":{\"content\":\"Привет\"},\"finish_reason\":null}]}\n\n"
                + chunk(" мир", "stop") + event(Map.of("choices", List.of()))
                + chunk("", "stop") + "data: [DONE]\n\n";
        assertEquals("Привет мир", OpenRouterClient.readStream(input(data), first::incrementAndGet));
        assertEquals(1, first.get());
    }
    @Test void rejectsTruncationLengthEmptyOutputAndMidstreamErrors() {
        for (String data : List.of(chunk("abc", "stop"), chunk("abc", "length") + "data: [DONE]\n\n",
                chunk("", "stop") + "data: [DONE]\n\n",
                event(Map.of("error", Map.of("message", "secret provider body"))),
                "data: malformed\n\n")) {
            var ex = assertThrows(GenerationFailure.class, () -> OpenRouterClient.readStream(input(data), () -> {}));
            assertFalse(ex.getMessage().contains("secret"));
        }
    }
    @Test void missingSecretDoesNotMakeNetworkRequest() {
        assertEquals(GenerationFailure.Code.NOT_CONFIGURED, assertThrows(GenerationFailure.class,
                () -> new OpenRouterClient("", "test:free", Duration.ofSeconds(1), 4000)
                        .complete("system", "user", () -> {})).code());
    }
    @Test void acceptsLargeSseOverheadWithoutTreatingItAsAnswerText() throws Exception {
        String overhead = (": " + "x".repeat(190) + "\n\n").repeat(11000);
        assertTrue(overhead.length() > 2_000_000);
        String data = overhead + chunk("answer", "stop") + "data: [DONE]\n\n";
        assertEquals("answer", OpenRouterClient.readStream(input(data), () -> {}));
    }
    @Test void rejectsPaidModelConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenRouterClient("fake", "paid-model", Duration.ofSeconds(1), 4000));
    }
    @Test void realHttpAdapterSendsStreamingRequestAndMapsRateLimit() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/chat", exchange -> {
            assertEquals("Bearer synthetic-test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            Map<?, ?> body = Json.read(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(true, body.get("stream"));
            assertEquals(Map.of("enabled", false), body.get("reasoning"));
            byte[] bytes = "do not expose upstream body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client = new OpenRouterClient("synthetic-test-key", "test:free",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/chat"), Duration.ofSeconds(3), 4000);
            assertEquals(GenerationFailure.Code.RATE_LIMITED,
                    assertThrows(GenerationFailure.class, () -> client.complete("system", "user", () -> {})).code());
        } finally { server.stop(0); }
    }
    @Test void stalledBodyIsBoundedByTotalTimeout() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(": waiting\n\n".getBytes(StandardCharsets.UTF_8)); out.flush();
                try { Thread.sleep(3000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            var client = new OpenRouterClient("synthetic", "test:free",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/chat"), Duration.ofMillis(250), 4000);
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(GenerationFailure.Code.MODEL_TIMEOUT,
                    assertThrows(GenerationFailure.class, () -> client.complete("s", "u", () -> {})).code()));
        } finally { server.stop(0); }
    }
    @Test void outputLimitAndRefusalHaveDistinctSafeCodes() {
        assertEquals(GenerationFailure.Code.MODEL_OUTPUT_LIMIT, assertThrows(GenerationFailure.class,
                ()->OpenRouterClient.readStream(input(chunk("partial", "length")),()->{})).code());
        assertEquals(GenerationFailure.Code.MODEL_REFUSED, assertThrows(GenerationFailure.class,
                ()->OpenRouterClient.readStream(input(chunk("", "content_filter")),()->{})).code());
    }
}
