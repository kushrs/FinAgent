package com.finagent.loanservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.loanservice.model.LoanApplication;
import com.finagent.loanservice.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanDecisionConsumer {

    private final LoanApplicationRepository loanRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "loan.decision.made", groupId = "loan-service-decision-group")
    @Transactional
    public void consume(String message) {
        log.info("Received loan decision raw message: {}", message);
        try {
            LoanDecisionMadeEvent event = objectMapper.readValue(message, LoanDecisionMadeEvent.class);
            log.info("Successfully decoded loan decision event for loan: {} with status: {}", event.getLoanId(), event.getStatus());

            loanRepository.findById(event.getLoanId()).ifPresentOrElse(loan -> {
                log.info("Updating loan {} status to {} and saving explanation", loan.getId(), event.getStatus());
                loan.setStatus(event.getStatus());
                loan.setExplanation(event.getExplanation());
                loanRepository.save(loan);
            }, () -> {
                log.error("Received decision for non-existent loan ID: {}", event.getLoanId());
            });

        } catch (Exception ex) {
            log.error("Failed to parse or process loan decision raw message", ex);
        }
    }
}
