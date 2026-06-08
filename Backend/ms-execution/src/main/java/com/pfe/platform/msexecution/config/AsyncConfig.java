package com.pfe.platform.msexecution.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Defines the primary TaskExecutor used by @Async methods.
 *
 * Without this, Spring sees the WebSocket broker thread pools
 * (clientInboundChannelExecutor, clientOutboundChannelExecutor, etc.)
 * and logs a warning because it can't pick one automatically.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core threads — one per concurrent evaluation (typically 1-3)
        executor.setCorePoolSize(3);
        // Max threads for burst of evaluations
        executor.setMaxPoolSize(8);
        // Queue for pending evaluations
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("UxEval-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
