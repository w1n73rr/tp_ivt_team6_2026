package tech.genna.TY3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class GenerationConfig {
    @Bean(name = "generationExecutor")
    ThreadPoolTaskExecutor generationExecutor(@Value("${generation.workers:2}") int workers) {
        if (workers < 1 || workers > 16) throw new IllegalArgumentException("Generation workers must be 1..16");
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("generation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
