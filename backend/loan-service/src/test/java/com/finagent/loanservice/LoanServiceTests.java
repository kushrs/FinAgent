package com.finagent.loanservice;

import com.finagent.loanservice.dto.LoanRequest;
import com.finagent.loanservice.messaging.CustomerProfileUpdatedEvent;
import com.finagent.loanservice.messaging.LoanDecisionMadeEvent;
import com.finagent.loanservice.model.LoanApplication;
import com.finagent.loanservice.model.LoanStatus;
import com.finagent.loanservice.repository.LoanApplicationRepository;
import com.finagent.loanservice.security.UserPrincipal;
import com.finagent.loanservice.service.LoanService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:loandb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "customer.profile.updated", "loan.submitted", "loan.decision.made" })
public class LoanServiceTests {

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanApplicationRepository loanRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> loanSubmittedConsumer;

    @BeforeEach
    public void setup() {
        loanRepository.deleteAll();

        // Configure Kafka consumer to listen to embedded Kafka broker on "loan.submitted" topic
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("loan-test-group", "true", embeddedKafkaBroker);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        );
        loanSubmittedConsumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(loanSubmittedConsumer, "loan.submitted");
    }

    @AfterEach
    public void tearDown() {
        if (loanSubmittedConsumer != null) {
            loanSubmittedConsumer.close();
        }
    }

    @Test
    public void testSubmitLoanAndRbacAccess() {
        UUID userId = UUID.randomUUID();
        LoanRequest request = new LoanRequest(new BigDecimal("15000.00"), 36);

        // Act
        LoanApplication loan = loanService.applyForLoan(userId, request);

        // Assert DB entry
        assertNotNull(loan.getId());
        assertEquals(userId, loan.getUserId());
        assertEquals(new BigDecimal("15000.00"), loan.getAmount());
        assertEquals(36, loan.getTermMonths());
        assertEquals(LoanStatus.SUBMITTED, loan.getStatus());

        // Verify RBAC access checks
        UserPrincipal ownerPrincipal = new UserPrincipal(userId, "owner@example.com", "CUSTOMER");
        UserPrincipal officerPrincipal = new UserPrincipal(UUID.randomUUID(), "officer@example.com", "LOAN_OFFICER");
        UserPrincipal intruderPrincipal = new UserPrincipal(UUID.randomUUID(), "intruder@example.com", "CUSTOMER");

        // Owner can access
        assertNotNull(loanService.getLoanById(loan.getId(), ownerPrincipal));

        // Loan Officer can access
        assertNotNull(loanService.getLoanById(loan.getId(), officerPrincipal));

        // Intruder cannot access and throws AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> loanService.getLoanById(loan.getId(), intruderPrincipal));
    }

    @Test
    public void testProfileUpdateTransitionFlow() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "borrower@example.com";

        // Create an open loan application in SUBMITTED state
        LoanRequest request = new LoanRequest(new BigDecimal("50000.00"), 60);
        LoanApplication loan = loanService.applyForLoan(userId, request);
        assertEquals(LoanStatus.SUBMITTED, loan.getStatus());

        // Construct mock profile updated event
        CustomerProfileUpdatedEvent profileEvent = CustomerProfileUpdatedEvent.builder()
                .userId(userId)
                .email(email)
                .ssn("999-99-9999")
                .annualIncome(new BigDecimal("120000.00"))
                .employmentStatus("EMPLOYED")
                .creditScore(720)
                .build();

        // Publish profile update event to "customer.profile.updated" to trigger consumer
        kafkaTemplate.send("customer.profile.updated", userId.toString(), profileEvent);

        // Retrieve generated "loan.submitted" event
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                loanSubmittedConsumer,
                "loan.submitted",
                java.time.Duration.ofSeconds(10)
        );
        assertNotNull(record);
        assertEquals(loan.getId().toString(), record.key());

        String jsonPayload = record.value();
        assertTrue(jsonPayload.contains("loanId"));
        assertTrue(jsonPayload.contains(userId.toString()));
        assertTrue(jsonPayload.contains("720")); // credit score
        assertTrue(jsonPayload.contains("120000")); // annual income

        // Verify loan status in DB is updated to UNDER_REVIEW and snapshot parameters are set
        LoanApplication updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.UNDER_REVIEW, updatedLoan.getStatus());
        assertEquals(720, updatedLoan.getCreditScoreSnapshot());
        assertEquals(new BigDecimal("120000.00"), updatedLoan.getAnnualIncomeSnapshot());
    }

    @Test
    public void testLoanDecisionConsumerUpdateFlow() throws Exception {
        UUID userId = UUID.randomUUID();
        LoanRequest request = new LoanRequest(new BigDecimal("12000.00"), 12);
        LoanApplication loan = loanService.applyForLoan(userId, request);
        assertEquals(LoanStatus.SUBMITTED, loan.getStatus());

        // Emit a mock LoanDecisionMadeEvent directly to loan.decision.made
        LoanDecisionMadeEvent decisionEvent = LoanDecisionMadeEvent.builder()
                .loanId(loan.getId())
                .userId(userId)
                .status(LoanStatus.APPROVED)
                .riskScore("Low")
                .fraudRisk("Pass")
                .documentStatus("Pass")
                .explanation("Safe borrower credentials verified by AI.")
                .build();

        kafkaTemplate.send("loan.decision.made", loan.getId().toString(), decisionEvent);

        // Allow up to 5 seconds for the consumer thread to process and update DB
        boolean updated = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(250);
            LoanApplication updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
            if (updatedLoan.getStatus() == LoanStatus.APPROVED) {
                assertEquals("Safe borrower credentials verified by AI.", updatedLoan.getExplanation());
                updated = true;
                break;
            }
        }
        assertTrue(updated, "Loan should be transitioned to APPROVED by the consumer");
    }
}
