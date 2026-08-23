package com.finagent.loanservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "loan.submitted";

    public void publishLoanSubmitted(LoanSubmittedEvent event) {
        log.info("Publishing loan.submitted event for loan application: {}", event.getLoanId());
        try {
            kafkaTemplate.send(TOPIC, event.getLoanId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to publish loan.submitted event for loan application: {}", event.getLoanId(), e);
        }
    }
}
