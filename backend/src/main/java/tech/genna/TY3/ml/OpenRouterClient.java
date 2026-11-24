package tech.genna.TY3.ml;

import java.net.URI;
import java.time.Duration;

/** OpenRouter keeps its explicit free-model restriction. */
public final class OpenRouterClient extends ChatCompletionsClient {
    public OpenRouterClient(String key, String model, Duration timeout, int maxTokens) {
        this(key, model, timeout, maxTokens, false);
    }

    public OpenRouterClient(String key, String model, Duration timeout, int maxTokens, boolean reasoning) {
        super(key, model, URI.create("https://openrouter.ai/api/v1/chat/completions"),
                timeout, maxTokens, reasoning, false);
    }

    OpenRouterClient(String key, String model, URI endpoint, Duration timeout, int maxTokens) {
        super(key, model, endpoint, timeout, maxTokens, false, false);
    }
}
