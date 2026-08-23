package com.finagent.loanservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finagent.loanservice.model.LoanApplication;
import com.finagent.loanservice.model.LoanStatus;
import com.finagent.loanservice.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerProfileUpdatedConsumer {

    private final LoanApplicationRepository loanRepository;
    private final LoanEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer.profile.updated", groupId = "loan-service-group")
    @Transactional
    public void consume(String message) {
        log.info("Received customer profile updated raw message: {}", message);
        try {
            CustomerProfileUpdatedEvent event = objectMapper.readValue(message, CustomerProfileUpdatedEvent.class);
            log.info("Successfully decoded customer profile updated event for user: {}", event.getUserId());

            if (event.getCreditScore() == null) {
                log.info("Credit score is null for user {}, skipping loan snapshotting", event.getUserId());
                return;
            }

            List<LoanApplication> submittedLoans = loanRepository.findByUserIdAndStatus(event.getUserId(), LoanStatus.SUBMITTED);
            if (submittedLoans.isEmpty()) {
                log.info("No SUBMITTED loan applications found for user: {}", event.getUserId());
                return;
            }

            for (LoanApplication loan : submittedLoans) {
                log.info("Snapshotting profile data and transitioning status to UNDER_REVIEW for loan: {}", loan.getId());
                loan.setCreditScoreSnapshot(event.getCreditScore());
                loan.setAnnualIncomeSnapshot(event.getAnnualIncome());
                loan.setStatus(LoanStatus.UNDER_REVIEW);
                loanRepository.save(loan);

                // Publish loan.submitted event to Kafka
                LoanSubmittedEvent submittedEvent = LoanSubmittedEvent.builder()
                        .loanId(loan.getId())
                        .userId(loan.getUserId())
                        .amount(loan.getAmount())
                        .termMonths(loan.getTermMonths())
                        .creditScore(event.getCreditScore())
                        .annualIncome(event.getAnnualIncome())
                        .ssn(event.getSsn())
                        .address(event.getAddress())
                        .build();

                eventPublisher.publishLoanSubmitted(submittedEvent);
            }
        } catch (Exception ex) {
            log.error("Failed to parse or process customer profile updated raw message", ex);
        }
    }
}
