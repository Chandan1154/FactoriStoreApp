package com.factori.platform.service;

public record BuildExecutionResult(
        String artifactStorageKey,
        String artifactSha256,
        String logStorageKey) {
}
