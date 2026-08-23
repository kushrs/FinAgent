package com.finagent.loanservice.service;

import com.finagent.loanservice.dto.LoanRequest;
import com.finagent.loanservice.exception.LoanApplicationNotFoundException;
import com.finagent.loanservice.model.LoanApplication;
import com.finagent.loanservice.model.LoanStatus;
import com.finagent.loanservice.repository.LoanApplicationRepository;
import com.finagent.loanservice.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanApplicationRepository loanRepository;

    @Transactional
    public LoanApplication applyForLoan(UUID userId, LoanRequest request) {
        LoanApplication loan = LoanApplication.builder()
                .userId(userId)
                .amount(request.getAmount())
                .termMonths(request.getTermMonths())
                .status(LoanStatus.SUBMITTED)
                .build();

        return loanRepository.save(loan);
    }

    public LoanApplication getLoanById(UUID loanId, UserPrincipal principal) {
        LoanApplication loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found with ID: " + loanId));

        // Enforce ownership checks for CUSTOMER roles
        if ("CUSTOMER".equals(principal.role()) && !loan.getUserId().equals(principal.id())) {
            throw new AccessDeniedException("Access Denied: You do not own this loan application");
        }

        return loan;
    }
}
