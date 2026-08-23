package com.finagent.gatewayservice.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider tokenProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // List of public paths (relative to gateyway path /api prefix)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/notifications/stream",
            "/api/analytics/metrics",
            "/actuator/health",
            "/actuator/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        log.info("Gateway routing request: {} {}", exchange.getRequest().getMethod(), path);

        // 1. Bypass check for public paths
        boolean isPublic = PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isPublic) {
            log.info("Request to public path allowed: {}", path);
            return chain.filter(exchange);
        }

        // 2. Validate token on protected path
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header on protected route: {}", path);
            return handleUnauthorized(exchange, "Missing or invalid Authorization header");
        }

        String jwtToken = authHeader.substring(7);
        if (!tokenProvider.validateToken(jwtToken)) {
            log.warn("JWT token signature validation failed for route: {}", path);
            return handleUnauthorized(exchange, "JWT signature validation failed");
        }

        try {
            // 3. Extract claims and propagate as headers downstream
            Claims claims = tokenProvider.getClaimsFromToken(jwtToken);
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);

            if (userId == null || role == null) {
                log.warn("Claims 'userId' or 'role' missing from token for route: {}", path);
                return handleUnauthorized(exchange, "Invalid token identity claims");
            }

            log.info("Token validated. Injecting headers: X-User-Id={}, X-User-Role={} for path={}", userId, role, path);
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception ex) {
            log.error("Exception parsing token claims for path: {}", path, ex);
            return handleUnauthorized(exchange, "Error processing security token");
        }
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // High priority order to run JWT check before request routing filters (such as Rate Limiting / Path Rewrite)
        return -10;
    }
}
