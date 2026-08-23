package com.finagent.customerservice.service;

import com.finagent.customerservice.messaging.CustomerEventPublisher;
import com.finagent.customerservice.messaging.CustomerProfileUpdatedEvent;
import com.finagent.customerservice.model.CustomerProfile;
import com.finagent.customerservice.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BureauService {

    private final CustomerProfileRepository profileRepository;
    private final CustomerEventPublisher eventPublisher;
    private final Random random = new Random();

    @Async
    public void fetchCreditScoreAndEmit(UUID userId, String email, CustomerProfile profile) {
        log.info("Starting credit score fetching simulation from external bureau for user: {}", userId);
        try {
            // Simulate 2 seconds latency for credit bureau integration
            Thread.sleep(2000);

            // Generate credit score between 300 and 850
            int creditScore = random.nextInt(551) + 300;
            log.info("Successfully fetched credit score: {} for user: {}", creditScore, userId);

            profile.setCreditScore(creditScore);
            profileRepository.save(profile);

            CustomerProfileUpdatedEvent event = CustomerProfileUpdatedEvent.builder()
                    .userId(userId)
                    .email(email)
                    .ssn(profile.getSsn())
                    .address(profile.getAddress())
                    .annualIncome(profile.getAnnualIncome())
                    .employmentStatus(profile.getEmploymentStatus())
                    .creditScore(creditScore)
                    .build();

            eventPublisher.publishProfileUpdated(event);

        } catch (InterruptedException e) {
            log.error("Credit score fetching simulation interrupted for user: {}", userId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error occurred while simulating credit score fetch for user: {}", userId, e);
        }
    }
}
