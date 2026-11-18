package com.factori.platform.service;

import com.factori.platform.service.AuthoritativeOrderMetricsProvider.OrderMetrics;
import java.util.Objects;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EligibilityService {
    private final ObjectProvider<AuthoritativeOrderMetricsProvider> metricsProvider;
    private final EligibilityPolicy eligibilityPolicy;

    public EligibilityService(
            ObjectProvider<AuthoritativeOrderMetricsProvider> metricsProvider,
            EligibilityPolicy eligibilityPolicy) {
        this.metricsProvider = metricsProvider;
        this.eligibilityPolicy = eligibilityPolicy;
    }

    public EligibilityStatus get(UUID retailerId) {
        AuthoritativeOrderMetricsProvider provider = metricsProvider.getIfAvailable();
        if (provider == null) {
            throw new FactoriIntegrationUnavailableException(
                    "Authoritative retailer order metrics are not configured.");
        }
        OrderMetrics metrics = provider.loadSinceStoreLaunch(retailerId);
        if (metrics == null) {
            throw new IllegalStateException("Order metrics provider returned no result.");
        }
        Objects.requireNonNull(metrics.measuredAt(), "Order metrics must include measuredAt.");
        EligibilityPolicy.Eligibility result = eligibilityPolicy.evaluate(
                metrics.completedOrders(),
                metrics.qualifyingSalesInInr());
        return new EligibilityStatus(
                result.completedOrders(),
                result.qualifyingSalesInInr(),
                result.eligible(),
                metrics.measuredAt());
    }

    public record EligibilityStatus(
            long completedOrders,
            java.math.BigDecimal qualifyingSalesInInr,
            boolean eligible,
            Instant measuredAt) {
    }

    public static class FactoriIntegrationUnavailableException extends RuntimeException {
        public FactoriIntegrationUnavailableException(String message) {
            super(message);
        }
    }
}
