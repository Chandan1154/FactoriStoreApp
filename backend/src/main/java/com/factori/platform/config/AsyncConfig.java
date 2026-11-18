package com.factori.platform.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {
    @Bean("buildWorkerExecutor")
    ThreadPoolTaskExecutor buildWorkerExecutor(FactoriProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.buildWorkerThreads());
        executor.setMaxPoolSize(properties.buildWorkerThreads());
        executor.setQueueCapacity(properties.buildQueueCapacity());
        executor.setThreadNamePrefix("apk-build-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
