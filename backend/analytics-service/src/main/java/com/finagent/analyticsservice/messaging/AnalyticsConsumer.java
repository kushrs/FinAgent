package com.finagent.analyticsservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.analyticsservice.registry.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final MetricsRegistry metricsRegistry;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer.profile.updated", groupId = "analytics-service-group")
    public void consumeProfileUpdated(String message) {
        log.info("Analytics service received raw customer profile event: {}", message);
        try {
            // Check if profile was updated successfully
            JsonNode jsonNode = objectMapper.readTree(message);
            if (jsonNode.has("userId")) {
                metricsRegistry.incrementUsersRegistered();
                log.info("Incremented total registered users. Current count: {}", 
                        metricsRegistry.getTotalUsersRegistered());
            }
        } catch (Exception ex) {
            log.error("Failed to parse or process customer profile update message", ex);
        }
    }

    @KafkaListener(topics = "loan.decision.made", groupId = "analytics-service-group")
    public void consumeLoanDecision(String message) {
        log.info("Analytics service received raw loan decision event: {}", message);
        try {
            LoanDecisionMadeEvent event = objectMapper.readValue(message, LoanDecisionMadeEvent.class);
            metricsRegistry.recordLoanDecision(event.getAmount(), event.getStatus());
            log.info("Recorded loan decision. Loan: {}, Amount: {}, Status: {}. Total loans: {}, Total amount: {}", 
                    event.getLoanId(), event.getAmount(), event.getStatus(), 
                    metricsRegistry.getTotalLoansApplied(), metricsRegistry.getTotalLoanAmount());
        } catch (Exception ex) {
            log.error("Failed to parse or process loan decision made message", ex);
        }
    }
}
