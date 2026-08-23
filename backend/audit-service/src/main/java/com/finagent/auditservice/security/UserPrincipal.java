package com.finagent.auditservice.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class UserPrincipal {
    private UUID id;
    private String email;
    private String role;
}
