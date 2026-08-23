package com.finagent.gatewayservice.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // 1. Resolve key by X-User-Id header injected by JwtValidationFilter
            String userId = request.getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just("user_" + userId);
            }
            
            // 2. Resolve key by remote IP address fallback for public/anonymous endpoints
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just("ip_" + remoteAddress.getAddress().getHostAddress());
            }
            
            return Mono.just("ip_anonymous");
        };
    }
}
