package com.finagent.notificationservice.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDecisionMadeEvent {
    private UUID loanId;
    private UUID userId;
    private BigDecimal amount;
    private String status;
    private String riskScore;
    private String fraudRisk;
    private String documentStatus;
    private String explanation;
}
