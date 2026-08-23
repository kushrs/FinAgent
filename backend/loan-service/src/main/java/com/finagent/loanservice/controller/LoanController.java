package com.finagent.loanservice.controller;

import com.finagent.loanservice.dto.LoanRequest;
import com.finagent.loanservice.dto.LoanResponse;
import com.finagent.loanservice.model.LoanApplication;
import com.finagent.loanservice.security.UserPrincipal;
import com.finagent.loanservice.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loan Service Controller", description = "Endpoints for submitting and retrieving loan applications")
@SecurityRequirement(name = "Bearer Authentication")
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/apply")
    @Operation(summary = "Submit a new loan application")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Loan application successfully submitted"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload supplied")
    })
    public ResponseEntity<LoanResponse> apply(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LoanRequest request
    ) {
        LoanApplication loan = loanService.applyForLoan(principal.id(), request);
        return new ResponseEntity<>(mapToResponse(loan), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve loan application details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan details retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Access denied to this resource"),
            @ApiResponse(responseCode = "404", description = "Loan application not found")
    })
    public ResponseEntity<LoanResponse> getLoanById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LoanApplication loan = loanService.getLoanById(id, principal);
        return ResponseEntity.ok(mapToResponse(loan));
    }

    private LoanResponse mapToResponse(LoanApplication loan) {
        return LoanResponse.builder()
                .id(loan.getId())
                .userId(loan.getUserId())
                .amount(loan.getAmount())
                .termMonths(loan.getTermMonths())
                .status(loan.getStatus())
                .creditScoreSnapshot(loan.getCreditScoreSnapshot())
                .annualIncomeSnapshot(loan.getAnnualIncomeSnapshot())
                .explanation(loan.getExplanation())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .build();
    }
}
