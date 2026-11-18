package com.factori.platform.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EligibilityPolicy {
    public static final long MIN_COMPLETED_ORDERS = 500;
    public static final BigDecimal MIN_SALES_IN_INR = new BigDecimal("250000.00");

    public Eligibility evaluate(long completedOrderCount, BigDecimal qualifyingSalesInInr) {
        if (completedOrderCount < 0) {
            throw new IllegalArgumentException("Order count cannot be negative.");
        }
        Objects.requireNonNull(qualifyingSalesInInr, "qualifyingSalesInInr");
        if (qualifyingSalesInInr.signum() < 0) {
            throw new IllegalArgumentException("Sales total cannot be negative.");
        }
        return new Eligibility(
                completedOrderCount,
                qualifyingSalesInInr,
                completedOrderCount >= MIN_COMPLETED_ORDERS
                        || qualifyingSalesInInr.compareTo(MIN_SALES_IN_INR) >= 0);
    }

    public record Eligibility(
            long completedOrders,
            BigDecimal qualifyingSalesInInr,
            boolean eligible) {
    }
}
