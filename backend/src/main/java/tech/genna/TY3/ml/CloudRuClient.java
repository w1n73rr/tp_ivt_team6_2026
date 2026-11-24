package tech.genna.TY3.ml;

import java.net.URI;
import java.time.Duration;

/** Foundation Models uses a fixed HTTPS endpoint and its own API key. */
public final class CloudRuClient extends ChatCompletionsClient {
    public CloudRuClient(String key, String model, Duration timeout, int maxTokens) {
        this(key, model, URI.create("https://foundation-models.api.cloud.ru/v1/chat/completions"), timeout, maxTokens);
    }

    CloudRuClient(String key, String model, URI endpoint, Duration timeout, int maxTokens) {
        super(key, model, endpoint, timeout, maxTokens, false, true);
    }
}
