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
public class CustomerProfileUpdatedEvent {
    private UUID userId;
    private String email;
    private String ssn;
    private String address;
    private BigDecimal annualIncome;
    private String employmentStatus;
    private Integer creditScore;
}
