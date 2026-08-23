package com.finagent.analyticsservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.analyticsservice.controller.AnalyticsController;
import com.finagent.analyticsservice.dto.AnalyticsMetricsResponse;
import com.finagent.analyticsservice.messaging.LoanDecisionMadeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "customer.profile.updated", "loan.decision.made" })
public class AnalyticsServiceTests {

    @Autowired
    private AnalyticsController analyticsController;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testAnalyticsConsumersAndAggregatorFlow() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        // 1. Simulate 2 profile updates
        String profilePayload1 = "{\"userId\":\"" + userId1 + "\",\"creditScore\":720}";
        String profilePayload2 = "{\"userId\":\"" + userId2 + "\",\"creditScore\":680}";

        kafkaTemplate.send("customer.profile.updated", userId1.toString(), profilePayload1);
        kafkaTemplate.send("customer.profile.updated", userId2.toString(), profilePayload2);

        // 2. Simulate 2 loan decisions:
        // Loan 1: APPROVED, 30,000.00
        LoanDecisionMadeEvent loan1 = LoanDecisionMadeEvent.builder()
                .loanId(UUID.randomUUID())
                .userId(userId1)
                .amount(new BigDecimal("30000.00"))
                .status("APPROVED")
                .riskScore("Low")
                .fraudRisk("Pass")
                .documentStatus("Pass")
                .explanation("Good credit profile.")
                .build();

        // Loan 2: REJECTED, 15,000.00
        LoanDecisionMadeEvent loan2 = LoanDecisionMadeEvent.builder()
                .loanId(UUID.randomUUID())
                .userId(userId2)
                .amount(new BigDecimal("15000.00"))
                .status("REJECTED")
                .riskScore("High")
                .fraudRisk("Pass")
                .documentStatus("Pass")
                .explanation("High debt ratio.")
                .build();

        kafkaTemplate.send("loan.decision.made", loan1.getLoanId().toString(), objectMapper.writeValueAsString(loan1));
        kafkaTemplate.send("loan.decision.made", loan2.getLoanId().toString(), objectMapper.writeValueAsString(loan2));

        // 3. Poll registry state via Controller
        boolean verified = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(250);
            AnalyticsMetricsResponse summary = analyticsController.getMetricsSummary();
            if (summary.getTotalUsersRegistered() == 2 && summary.getTotalLoansApplied() == 2) {
                assertEquals(new BigDecimal("45000.00"), summary.getTotalLoanAmount());
                assertEquals(0.5, summary.getApprovedRate(), 0.001);
                assertEquals(0.5, summary.getRejectedRate(), 0.001);
                assertEquals(0.0, summary.getUnderReviewRate(), 0.001);
                verified = true;
                break;
            }
        }
        assertTrue(verified, "MetricsRegistry should correctly process profile updates and decision records");
    }
}
