package com.finagent.auditservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.auditservice.model.LoanAuditRecord;
import com.finagent.auditservice.repository.LoanAuditRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final LoanAuditRecordRepository auditRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "loan.decision.made", groupId = "audit-service-group")
    public void consume(String message) {
        log.info("Audit service received raw decision message: {}", message);
        try {
            LoanDecisionMadeEvent event = objectMapper.readValue(message, LoanDecisionMadeEvent.class);
            log.info("Successfully decoded loan decision event for auditing. Loan: {}, User: {}, Status: {}", 
                    event.getLoanId(), event.getUserId(), event.getStatus());

            LoanAuditRecord record = LoanAuditRecord.builder()
                    .loanId(event.getLoanId())
                    .userId(event.getUserId())
                    .amount(event.getAmount())
                    .status(event.getStatus())
                    .explanation(event.getExplanation())
                    .auditedAt(LocalDateTime.now())
                    .build();

            auditRepository.save(record);
            log.info("Successfully committed audit record for loan application: {}", record.getLoanId());

        } catch (Exception ex) {
            log.error("Failed to parse or process decision audit message", ex);
        }
    }
}
