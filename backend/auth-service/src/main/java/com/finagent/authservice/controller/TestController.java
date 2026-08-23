package com.finagent.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Tag(name = "RBAC Test Controller", description = "Endpoints to test role-based access control restrictions")
@SecurityRequirement(name = "Bearer Authentication")
public class TestController {

    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Endpoint restricted to CUSTOMER or ADMIN")
    public ResponseEntity<Map<String, String>> testCustomer() {
        return ResponseEntity.ok(createMessage("Success: You accessed the CUSTOMER protected endpoint."));
    }

    @GetMapping("/officer")
    @PreAuthorize("hasRole('LOAN_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Endpoint restricted to LOAN_OFFICER or ADMIN")
    public ResponseEntity<Map<String, String>> testOfficer() {
        return ResponseEntity.ok(createMessage("Success: You accessed the LOAN_OFFICER protected endpoint."));
    }

    @GetMapping("/risk")
    @PreAuthorize("hasRole('RISK_ANALYST') or hasRole('ADMIN')")
    @Operation(summary = "Endpoint restricted to RISK_ANALYST or ADMIN")
    public ResponseEntity<Map<String, String>> testRisk() {
        return ResponseEntity.ok(createMessage("Success: You accessed the RISK_ANALYST protected endpoint."));
    }

    @GetMapping("/fraud")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    @Operation(summary = "Endpoint restricted to FRAUD_ANALYST or ADMIN")
    public ResponseEntity<Map<String, String>> testFraud() {
        return ResponseEntity.ok(createMessage("Success: You accessed the FRAUD_ANALYST protected endpoint."));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Endpoint restricted to ADMIN only")
    public ResponseEntity<Map<String, String>> testAdmin() {
        return ResponseEntity.ok(createMessage("Success: You accessed the ADMIN protected endpoint."));
    }

    private Map<String, String> createMessage(String text) {
        Map<String, String> body = new HashMap<>();
        body.put("status", "AUTHORIZED");
        body.put("content", text);
        return body;
    }
}
