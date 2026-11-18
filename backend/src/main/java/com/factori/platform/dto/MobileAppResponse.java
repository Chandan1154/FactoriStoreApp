package com.factori.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record MobileAppResponse(
        UUID appId,
        String applicationId,
        String storeName,
        String storeUrl,
        String status,
        Instant createdAt,
        AppBuildResponse latestBuild) {
}
