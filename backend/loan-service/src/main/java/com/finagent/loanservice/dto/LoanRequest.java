package com.finagent.loanservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanRequest {

    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Loan amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Term months is required")
    @Min(value = 6, message = "Minimum loan term is 6 months")
    @Max(value = 360, message = "Maximum loan term is 360 months")
    private Integer termMonths;
}
