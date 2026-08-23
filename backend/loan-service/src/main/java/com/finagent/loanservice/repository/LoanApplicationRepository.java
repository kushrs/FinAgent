package com.finagent.loanservice.repository;

import com.finagent.loanservice.model.LoanApplication;
import com.finagent.loanservice.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
    List<LoanApplication> findByUserIdAndStatus(UUID userId, LoanStatus status);
}
