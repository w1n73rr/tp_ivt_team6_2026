package tech.genna.TY3.service.generation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** One deadline for file retrieval, extraction, model calls and result handling. */
@Component
public class GenerationTimeout {
    private final Duration duration;

    public GenerationTimeout(
            @Value("${generation.job-timeout-seconds:${GENERATION_JOB_TIMEOUT_SECONDS:0}}") long configured,
            @Value("${ml.total-timeout-seconds:${ML_TOTAL_TIMEOUT_SECONDS:240}}") long mlSeconds) {
        if (configured < 0 || configured > 3600 || mlSeconds < 1 || mlSeconds > 600)
            throw new IllegalArgumentException("Invalid generation timeout");
        duration = Duration.ofSeconds(configured == 0 ? Math.max(300, mlSeconds + 120) : configured);
    }

    public Duration duration() { return duration; }
}
