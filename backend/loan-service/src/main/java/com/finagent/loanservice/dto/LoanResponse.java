package com.finagent.loanservice.dto;

import com.finagent.loanservice.model.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {
    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private Integer termMonths;
    private LoanStatus status;
    private Integer creditScoreSnapshot;
    private BigDecimal annualIncomeSnapshot;
    private String explanation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
