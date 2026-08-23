package com.finagent.customerservice.dto;

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
public class ProfileResponse {
    private UUID id;
    private UUID userId;
    private String ssn;
    private String address;
    private BigDecimal annualIncome;
    private String employmentStatus;
    private Integer creditScore;
    private LocalDateTime createdAt;
}
