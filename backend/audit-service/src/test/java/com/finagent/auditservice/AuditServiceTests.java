package com.finagent.auditservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.auditservice.controller.AuditController;
import com.finagent.auditservice.messaging.LoanDecisionMadeEvent;
import com.finagent.auditservice.model.LoanAuditRecord;
import com.finagent.auditservice.repository.LoanAuditRecordRepository;
import com.finagent.auditservice.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "loan.decision.made" })
public class AuditServiceTests {

    @Autowired
    private LoanAuditRecordRepository auditRepository;

    @Autowired
    private AuditController auditController;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        auditRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testAuditConsumerAndAdminAccessControls() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 1. Build decision event
        LoanDecisionMadeEvent decision = LoanDecisionMadeEvent.builder()
                .loanId(loanId)
                .userId(userId)
                .amount(new BigDecimal("45000.00"))
                .status("APPROVED")
                .riskScore("Low")
                .fraudRisk("Pass")
                .documentStatus("Pass")
                .explanation("Pre-approved limits.")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(decision);

        // 2. Publish to Kafka
        kafkaTemplate.send("loan.decision.made", loanId.toString(), jsonPayload);

        // 3. Verify that the consumer asynchronously commits the record to database
        boolean saved = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(250);
            List<LoanAuditRecord> records = auditRepository.findAll();
            if (!records.isEmpty()) {
                assertEquals(1, records.size());
                LoanAuditRecord record = records.get(0);
                assertEquals(loanId, record.getLoanId());
                assertEquals(userId, record.getUserId());
                assertEquals(new BigDecimal("45000.00"), record.getAmount());
                assertEquals("APPROVED", record.getStatus());
                assertEquals("Pre-approved limits.", record.getExplanation());
                saved = true;
                break;
            }
        }
        assertTrue(saved, "Audit record should be processed and saved by the consumer");

        // 4. Verify RBAC Security Boundary
        // ADMIN principal
        UserPrincipal adminPrincipal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", "ADMIN");
        SimpleGrantedAuthority adminAuthority = new SimpleGrantedAuthority("ROLE_ADMIN");
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                adminPrincipal, null, Collections.singletonList(adminAuthority)
        );

        // CUSTOMER principal (Intruder)
        UserPrincipal customerPrincipal = new UserPrincipal(userId, "borrower@example.com", "CUSTOMER");
        SimpleGrantedAuthority customerAuthority = new SimpleGrantedAuthority("ROLE_CUSTOMER");
        UsernamePasswordAuthenticationToken customerAuth = new UsernamePasswordAuthenticationToken(
                customerPrincipal, null, Collections.singletonList(customerAuthority)
        );

        // Intruder gets AccessDeniedException
        SecurityContextHolder.getContext().setAuthentication(customerAuth);
        assertThrows(AccessDeniedException.class, () -> auditController.getLoanAuditTrails());

        // Admin gets access successfully
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
        List<LoanAuditRecord> audits = auditController.getLoanAuditTrails();
        assertEquals(1, audits.size());
    }
}
