package com.factori.platform.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "factori")
public record FactoriProperties(
        Path templateRoot,
        Path workspaceRoot,
        Path artifactRoot,
        String buildImage,
        int buildTimeoutSeconds,
        int buildQueueCapacity,
        int buildWorkerThreads) {
}
