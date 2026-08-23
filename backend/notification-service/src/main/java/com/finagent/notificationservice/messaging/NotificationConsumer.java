package com.finagent.notificationservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.notificationservice.controller.NotificationController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationController notificationController;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "loan.decision.made", groupId = "notification-service-group")
    public void consume(String message) {
        log.info("Notification service received raw decision message: {}", message);
        try {
            LoanDecisionMadeEvent event = objectMapper.readValue(message, LoanDecisionMadeEvent.class);
            
            // 1. Simulate sending SMS/Email by logging in structured format
            log.info("\n========================================================================\n" +
                     "[SMS/EMAIL SIMULATION] Sending alert to User: {}\n" +
                     "Subject: Loan Application {} Update\n" +
                     "Status: {}\n" +
                     "Reasoning: {}\n" +
                     "========================================================================",
                     event.getUserId(), event.getLoanId(), event.getStatus(), event.getExplanation());

            // 2. Broadcast via Server-Sent Events (SSE) stream
            notificationController.broadcastToUser(event.getUserId(), event);

        } catch (Exception ex) {
            log.error("Failed to parse or process decision notification message", ex);
        }
    }
}
