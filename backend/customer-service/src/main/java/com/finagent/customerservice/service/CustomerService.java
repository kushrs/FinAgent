package com.finagent.customerservice.service;

import com.finagent.customerservice.dto.ProfileRequest;
import com.finagent.customerservice.exception.ProfileNotFoundException;
import com.finagent.customerservice.model.CustomerProfile;
import com.finagent.customerservice.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerProfileRepository profileRepository;
    private final BureauService bureauService;

    public CustomerProfile getProfileByUserId(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException("Customer profile not found for user: " + userId));
    }

    @Transactional
    public CustomerProfile createOrUpdateProfile(UUID userId, String email, ProfileRequest request) {
        CustomerProfile profile = profileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.setSsn(request.getSsn());
                    existing.setAddress(request.getAddress());
                    existing.setAnnualIncome(request.getAnnualIncome());
                    existing.setEmploymentStatus(request.getEmploymentStatus());
                    // Clear score temporarily to indicate update in progress
                    existing.setCreditScore(null);
                    return existing;
                })
                .orElseGet(() -> CustomerProfile.builder()
                        .userId(userId)
                        .ssn(request.getSsn())
                        .address(request.getAddress())
                        .annualIncome(request.getAnnualIncome())
                        .employmentStatus(request.getEmploymentStatus())
                        .build());

        CustomerProfile savedProfile = profileRepository.save(profile);

        // Fetch credit score from bureau asynchronously
        bureauService.fetchCreditScoreAndEmit(userId, email, savedProfile);

        return savedProfile;
    }
}
