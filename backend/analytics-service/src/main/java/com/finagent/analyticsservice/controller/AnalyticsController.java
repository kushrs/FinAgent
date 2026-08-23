package com.finagent.analyticsservice.controller;

import com.finagent.analyticsservice.dto.AnalyticsMetricsResponse;
import com.finagent.analyticsservice.registry.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final MetricsRegistry metricsRegistry;

    @GetMapping("/metrics")
    public AnalyticsMetricsResponse getMetricsSummary() {
        log.info("Request received to fetch analytics metrics summary");
        return AnalyticsMetricsResponse.builder()
                .totalUsersRegistered(metricsRegistry.getTotalUsersRegistered())
                .totalLoansApplied(metricsRegistry.getTotalLoansApplied())
                .totalLoanAmount(metricsRegistry.getTotalLoanAmount())
                .approvedRate(metricsRegistry.getApprovedRate())
                .rejectedRate(metricsRegistry.getRejectedRate())
                .underReviewRate(metricsRegistry.getUnderReviewRate())
                .build();
    }
}
