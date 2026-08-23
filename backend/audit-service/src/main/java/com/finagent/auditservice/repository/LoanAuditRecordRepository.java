package com.finagent.auditservice.repository;

import com.finagent.auditservice.model.LoanAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanAuditRecordRepository extends JpaRepository<LoanAuditRecord, UUID> {
    List<LoanAuditRecord> findAllByOrderByAuditedAtDesc();
}
