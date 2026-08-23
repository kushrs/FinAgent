package com.finagent.loanservice.messaging;

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
public class LoanSubmittedEvent {
    private UUID loanId;
    private UUID userId;
    private BigDecimal amount;
    private Integer termMonths;
    private Integer creditScore;
    private BigDecimal annualIncome;
    private String ssn;
    private String address;
}
