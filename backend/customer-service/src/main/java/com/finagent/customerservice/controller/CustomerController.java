package com.finagent.customerservice.controller;

import com.finagent.customerservice.dto.ProfileRequest;
import com.finagent.customerservice.dto.ProfileResponse;
import com.finagent.customerservice.model.CustomerProfile;
import com.finagent.customerservice.security.UserPrincipal;
import com.finagent.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Profile Controller", description = "Endpoints for managing customer financial profiles")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/profile")
    @Operation(summary = "Create or update the authenticated user's financial profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile successfully created/updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload supplied")
    })
    public ResponseEntity<ProfileResponse> createOrUpdateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileRequest request
    ) {
        CustomerProfile profile = customerService.createOrUpdateProfile(principal.id(), principal.email(), request);
        return ResponseEntity.ok(mapToResponse(profile));
    }

    @GetMapping("/profile")
    @Operation(summary = "Retrieve the authenticated user's financial profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile details retrieved"),
            @ApiResponse(responseCode = "404", description = "Profile not found for this user")
    })
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        CustomerProfile profile = customerService.getProfileByUserId(principal.id());
        return ResponseEntity.ok(mapToResponse(profile));
    }

    private ProfileResponse mapToResponse(CustomerProfile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .ssn(profile.getSsn())
                .address(profile.getAddress())
                .annualIncome(profile.getAnnualIncome())
                .employmentStatus(profile.getEmploymentStatus())
                .creditScore(profile.getCreditScore())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}
