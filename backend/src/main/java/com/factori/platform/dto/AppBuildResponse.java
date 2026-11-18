package com.factori.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record AppBuildResponse(
        UUID buildId,
        String status,
        int attempt,
        String failureCode,
        Instant createdAt,
        Instant completedAt) {
}
