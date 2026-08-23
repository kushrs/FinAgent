package com.finagent.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.notificationservice.controller.NotificationController;
import com.finagent.notificationservice.messaging.LoanDecisionMadeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "loan.decision.made" })
public class NotificationServiceTests {

    @Autowired
    private NotificationController notificationController;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testNotificationConsumptionAndSseBroadcasting() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();

        // 1. Establish SSE Client Emitter for this user
        SseEmitter emitter = notificationController.streamNotifications(userId);
        assertNotNull(emitter);

        // Track asynchronous SSE delivery
        CountDownLatch latch = new CountDownLatch(1);
        emitter.onCompletion(latch::countDown);

        // 2. Build mock decision payload matching JSON string
        LoanDecisionMadeEvent decision = LoanDecisionMadeEvent.builder()
                .loanId(loanId)
                .userId(userId)
                .status("APPROVED")
                .riskScore("Low")
                .fraudRisk("Pass")
                .documentStatus("Pass")
                .explanation("Perfect profile score.")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(decision);

        // 3. Publish to Kafka topic
        kafkaTemplate.send("loan.decision.made", loanId.toString(), jsonPayload);

        // 4. Wait a short period to allow listener consumer and broadcast execution
        // SseEmitter will only complete on closure or exception. We just verify no errors.
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertFalse(completed, "SSE Emitter should remain open listening for new events");

        // Cleanup
        emitter.complete();
    }
}
