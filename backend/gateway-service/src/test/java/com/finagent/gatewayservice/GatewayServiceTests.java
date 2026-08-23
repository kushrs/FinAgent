package com.finagent.gatewayservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SPRING_REDIS_HOST=localhost",
        "SPRING_REDIS_PORT=6379",
        "AUTH_SERVICE_URL=http://localhost:8081",
        "LOAN_SERVICE_URL=http://localhost:8083"
})
@ActiveProfiles("test")
public class GatewayServiceTests {

    @Autowired
    private WebTestClient webTestClient;

    private static final String SECRET = "dGhpcy1pcy1hLXN1cGVyLXNlY3JldC1kZXZlbG9wbWVudC1rZXktZm9yLWZpbmFnZW50LW9zLW11c3QtYmUtMzItYnl0ZXMtcGxhY2Vob2xkZXI=";

    private String generateTestToken(UUID userId, String role, boolean expired) {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        
        long exp = expired ? -60000 : 900000; // negative duration if expired
        
        return Jwts.builder()
                .subject("test@example.com")
                .claim("userId", userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key)
                .compact();
    }

    @Test
    public void testPublicRouteBypassesAuthentication() {
        // GET /api/analytics/metrics is public - should bypass validation and NOT return 401 Unauthorized
        // Note: It might return 500/503/404 because the analytics-service is not running in this test, but it must NOT return 401
        webTestClient.get()
                .uri("/api/analytics/metrics")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    public void testProtectedRouteWithoutTokenReturnsUnauthorized() {
        // POST /api/loans/apply is protected - missing header must return 401
        webTestClient.post()
                .uri("/api/loans/apply")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo("Missing or invalid Authorization header");
    }

    @Test
    public void testProtectedRouteWithInvalidTokenReturnsUnauthorized() {
        // Invalid token signature must return 401
        webTestClient.post()
                .uri("/api/loans/apply")
                .header("Authorization", "Bearer invalidTokenContent")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo("JWT signature validation failed");
    }

    @Test
    public void testProtectedRouteWithValidTokenBypassesAuthentication() {
        UUID userId = UUID.randomUUID();
        String token = generateTestToken(userId, "CUSTOMER", false);

        // Valid token must pass validation and attempt routing (NOT return 401)
        webTestClient.post()
                .uri("/api/loans/apply")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }
}
