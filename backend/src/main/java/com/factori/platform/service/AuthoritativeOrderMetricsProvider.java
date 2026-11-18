package com.factori.platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface AuthoritativeOrderMetricsProvider {
    OrderMetrics loadSinceStoreLaunch(UUID retailerId);

    record OrderMetrics(
            long completedOrders,
            BigDecimal qualifyingSalesInInr,
            Instant measuredAt) {
    }
}
