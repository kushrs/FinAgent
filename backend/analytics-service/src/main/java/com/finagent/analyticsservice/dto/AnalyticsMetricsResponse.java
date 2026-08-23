package com.finagent.analyticsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsMetricsResponse {
    private long totalUsersRegistered;
    private long totalLoansApplied;
    private BigDecimal totalLoanAmount;
    private double approvedRate;
    private double rejectedRate;
    private double underReviewRate;
}
