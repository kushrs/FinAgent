package com.finagent.auditservice.controller;

import com.finagent.auditservice.model.LoanAuditRecord;
import com.finagent.auditservice.repository.LoanAuditRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final LoanAuditRecordRepository auditRepository;

    @GetMapping("/loans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<LoanAuditRecord> getLoanAuditTrails() {
        log.info("Request received to fetch all loan audit trails");
        return auditRepository.findAllByOrderByAuditedAtDesc();
    }
}
