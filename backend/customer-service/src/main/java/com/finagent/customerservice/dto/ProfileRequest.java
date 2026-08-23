package com.finagent.customerservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRequest {

    @NotBlank(message = "SSN is required")
    @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{4}$", message = "SSN must match the format XXX-XX-XXXX")
    private String ssn;

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Annual income is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Annual income must be greater than zero")
    private BigDecimal annualIncome;

    @NotBlank(message = "Employment status is required")
    @Pattern(regexp = "^(EMPLOYED|UNEMPLOYED|SELF_EMPLOYED|RETIRED)$", message = "Employment status must be one of: EMPLOYED, UNEMPLOYED, SELF_EMPLOYED, RETIRED")
    private String employmentStatus;
}
