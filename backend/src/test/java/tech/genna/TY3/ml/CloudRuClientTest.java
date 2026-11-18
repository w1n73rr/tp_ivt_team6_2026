package tech.genna.TY3.ml;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudRuClientTest {
    @Test void sendsCloudPayloadAndAcceptsStreamingJson() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/chat", exchange -> {
            requests.incrementAndGet();
            assertEquals("Bearer synthetic.cloud-key", exchange.getRequestHeaders().getFirst("Authorization"));
            var body = Json.read(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("deepseek-ai/DeepSeek-V4-Flash", body.get("model"));
            assertEquals(true, body.get("stream"));
            assertEquals(Map.of("type", "json_object"), body.get("response_format"));
            assertEquals(4000, ((Number) body.get("max_tokens")).intValue());
            assertFalse(body.containsKey("provider"));
            assertFalse(body.containsKey("reasoning"));
            byte[] data = (OpenRouterClientTest.chunk("{\"ok\":true}", "stop")
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        });
        server.start();
        try {
            var client = new CloudRuClient("synthetic.cloud-key", "deepseek-ai/DeepSeek-V4-Flash",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/chat"), Duration.ofSeconds(3), 4000);
            AtomicInteger first = new AtomicInteger();
            assertEquals("{\"ok\":true}", client.complete("system", "user", first::incrementAndGet));
            assertEquals(1, first.get());
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }

    @Test void insufficientBalanceHasSafeDiagnosticWithoutProviderBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] data = "private provider error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(402, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        });
        server.start();
        try {
            var client = new CloudRuClient("synthetic", "deepseek-ai/DeepSeek-V4-Flash",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/chat"), Duration.ofSeconds(3), 4000);
            var failure = assertThrows(GenerationFailure.class, () -> client.complete("s", "u", () -> {}));
            assertEquals(GenerationFailure.Code.MODEL_UNAVAILABLE, failure.code());
            assertEquals("PROVIDER_HTTP_402", failure.validationHint());
            assertFalse(failure.getMessage().contains("private"));
        } finally { server.stop(0); }
    }

    @Test void missingOrInvalidSecretFailsBeforeNetwork() {
        for (String key : new String[] {"", "bad\nheader"}) {
            var client = new CloudRuClient(key, "deepseek-ai/DeepSeek-V4-Flash", Duration.ofSeconds(1), 4000);
            assertEquals(GenerationFailure.Code.NOT_CONFIGURED, assertThrows(GenerationFailure.class,
                    () -> client.complete("system", "user", () -> {})).code());
        }
    }
}
