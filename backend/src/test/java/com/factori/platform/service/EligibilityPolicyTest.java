package com.factori.platform.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EligibilityPolicyTest {
    private final EligibilityPolicy policy = new EligibilityPolicy();

    @Test
    void unlocksWhenEitherThresholdIsMet() {
        assertTrue(policy.evaluate(500, BigDecimal.ZERO).eligible());
        assertTrue(policy.evaluate(0, new BigDecimal("250000.00")).eligible());
        assertFalse(policy.evaluate(499, new BigDecimal("249999.99")).eligible());
    }

    @Test
    void rejectsNegativeAuthoritativeMetrics() {
        assertThrows(IllegalArgumentException.class, () -> policy.evaluate(-1, BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.evaluate(0, new BigDecimal("-0.01")));
    }
}
