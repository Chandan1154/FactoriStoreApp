package com.factori.platform.service;

import java.util.UUID;

public record BuildContext(
        UUID tenantId,
        UUID appId,
        UUID buildId,
        String applicationId,
        String storeName,
        String storeUrl,
        String iconStorageKey,
        int versionCode,
        String versionName) {
}
