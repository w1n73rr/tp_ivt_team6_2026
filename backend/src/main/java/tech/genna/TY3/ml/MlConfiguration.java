package tech.genna.TY3.ml;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class MlConfiguration {
    @Bean
    public QuizGenerator quizGenerator(
            @Value("${ml.provider:${ML_PROVIDER:openrouter}}") String provider,
            @Value("${CLOUDRU_API_KEY:}") String cloudKey,
            @Value("${CLOUDRU_MODEL:deepseek-ai/DeepSeek-V4-Flash}") String cloudModel,
            @Value("${CLOUDRU_TIMEOUT_SECONDS:90}") long cloudTimeout,
            @Value("${CLOUDRU_MAX_OUTPUT_TOKENS:12000}") int cloudOutput,
            @Value("${ml.openrouter.api-key:${OPENROUTER_API_KEY:}}") String key,
            @Value("${ml.openrouter.model:${OPENROUTER_MODEL:nvidia/nemotron-3.5-super-vl:free}}") String model,
            @Value("${ml.openrouter.timeout-seconds:${OPENROUTER_TIMEOUT_SECONDS:90}}") long timeout,
            @Value("${ml.openrouter.max-output-tokens:${OPENROUTER_MAX_OUTPUT_TOKENS:12000}}") int output,
            @Value("${ml.openrouter.reasoning:${OPENROUTER_REASONING_ENABLED:false}}") boolean reasoning,
            @Value("${ml.total-timeout-seconds:${ML_TOTAL_TIMEOUT_SECONDS:240}}") long totalTimeout,
            @Value("${ml.max-input-chars:${ML_MAX_INPUT_CHARS:60000}}") int maxChars) {
        if (!provider.equals("openrouter") && !provider.equals("cloudru")) {
            throw new IllegalArgumentException("ML_PROVIDER must be openrouter or cloudru");
        }
        if (provider.equals("cloudru")) {
            timeout = cloudTimeout;
            output = cloudOutput;
        }
        if (maxChars < 1 || maxChars > 60000 || timeout < 1 || timeout > 600
                || totalTimeout < 1 || totalTimeout > 600) {
            throw new IllegalArgumentException("Invalid ML input/timeout limits");
        }
        ModelClient client = provider.equals("cloudru")
                ? new CloudRuClient(cloudKey, cloudModel, Duration.ofSeconds(timeout), output)
                : new OpenRouterClient(key, model, Duration.ofSeconds(timeout), output, reasoning);
        return new QuizGenerator(client,
                maxChars, Duration.ofSeconds(totalTimeout));
    }
}
