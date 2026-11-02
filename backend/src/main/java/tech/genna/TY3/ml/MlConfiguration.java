package tech.genna.TY3.ml;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class MlConfiguration {
    @Bean
    public QuizGenerator quizGenerator(
            @Value("${ml.openrouter.api-key:${OPENROUTER_API_KEY:}}") String key,
            @Value("${ml.openrouter.model:${OPENROUTER_MODEL:nvidia/nemotron-3.5-lightning:free}}") String model,
            @Value("${ml.openrouter.timeout-seconds:${OPENROUTER_TIMEOUT_SECONDS:90}}") long timeout,
            @Value("${ml.openrouter.max-output-tokens:${OPENROUTER_MAX_OUTPUT_TOKENS:12000}}") int output,
            @Value("${ml.openrouter.reasoning:${OPENROUTER_REASONING_ENABLED:false}}") boolean reasoning,
            @Value("${ml.total-timeout-seconds:${ML_TOTAL_TIMEOUT_SECONDS:240}}") long totalTimeout,
            @Value("${ml.max-input-chars:${ML_MAX_INPUT_CHARS:60000}}") int maxChars) {
        if (maxChars < 1 || maxChars > 60000 || timeout < 1 || timeout > 600
                || totalTimeout < 1 || totalTimeout > 600) {
            throw new IllegalArgumentException("Invalid ML input/timeout limits");
        }
        return new QuizGenerator(new OpenRouterClient(key, model, Duration.ofSeconds(timeout), output, reasoning),
                maxChars, Duration.ofSeconds(totalTimeout));
    }
}
