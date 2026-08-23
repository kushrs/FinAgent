package com.finagent.analyticsservice.registry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MetricsRegistry {

    private final AtomicLong totalUsersRegistered = new AtomicLong(0);
    private final AtomicLong totalLoansApplied = new AtomicLong(0);
    private final AtomicReference<BigDecimal> totalLoanAmount = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicLong approvedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong underReviewCount = new AtomicLong(0);

    public MetricsRegistry(MeterRegistry meterRegistry) {
        // Register micrometer gauges for Prometheus integration
        Gauge.builder("finagent.users.registered", totalUsersRegistered, AtomicLong::get)
                .description("Total number of registered customer profiles")
                .register(meterRegistry);

        Gauge.builder("finagent.loans.applied", totalLoansApplied, AtomicLong::get)
                .description("Total number of loan applications evaluated")
                .register(meterRegistry);

        Gauge.builder("finagent.loans.amount.total", totalLoanAmount, ref -> ref.get().doubleValue())
                .description("Total principal amount of applied loans")
                .register(meterRegistry);

        Gauge.builder("finagent.loans.approved", approvedCount, AtomicLong::get)
                .description("Total approved loan applications")
                .register(meterRegistry);

        Gauge.builder("finagent.loans.rejected", rejectedCount, AtomicLong::get)
                .description("Total rejected loan applications")
                .register(meterRegistry);

        Gauge.builder("finagent.loans.under_review", underReviewCount, AtomicLong::get)
                .description("Total loan applications flagged for manual review")
                .register(meterRegistry);
    }

    public void incrementUsersRegistered() {
        totalUsersRegistered.incrementAndGet();
    }

    public void recordLoanDecision(BigDecimal amount, String status) {
        totalLoansApplied.incrementAndGet();
        if (amount != null) {
            totalLoanAmount.updateAndGet(current -> current.add(amount));
        }

        if ("APPROVED".equalsIgnoreCase(status)) {
            approvedCount.incrementAndGet();
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            rejectedCount.incrementAndGet();
        } else if ("UNDER_REVIEW".equalsIgnoreCase(status)) {
            underReviewCount.incrementAndGet();
        }
    }

    public long getTotalUsersRegistered() {
        return totalUsersRegistered.get();
    }

    public long getTotalLoansApplied() {
        return totalLoansApplied.get();
    }

    public BigDecimal getTotalLoanAmount() {
        return totalLoanAmount.get();
    }

    public long getApprovedCount() {
        return approvedCount.get();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }

    public long getUnderReviewCount() {
        return underReviewCount.get();
    }

    public double getApprovedRate() {
        long total = totalLoansApplied.get();
        if (total == 0) return 0.0;
        return (double) approvedCount.get() / total;
    }

    public double getRejectedRate() {
        long total = totalLoansApplied.get();
        if (total == 0) return 0.0;
        return (double) rejectedCount.get() / total;
    }

    public double getUnderReviewRate() {
        long total = totalLoansApplied.get();
        if (total == 0) return 0.0;
        return (double) underReviewCount.get() / total;
    }
}
