package com.finagent.authservice.service;

import com.finagent.authservice.dto.*;
import com.finagent.authservice.exception.TokenInvalidException;
import com.finagent.authservice.exception.UserAlreadyExistsException;
import com.finagent.authservice.model.User;
import com.finagent.authservice.repository.UserRepository;
import com.finagent.authservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_REFRESH_TOKEN_PREFIX = "rt:";

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Cache refresh token in Redis mapping to userId with TTL
        long expiryMs = jwtTokenProvider.getRefreshTokenExpiryMs();
        redisTemplate.opsForValue().set(
                REDIS_REFRESH_TOKEN_PREFIX + refreshToken,
                user.getId().toString(),
                Duration.ofMillis(expiryMs)
        );

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new TokenInvalidException("Invalid refresh token");
        }

        String cachedUserId = redisTemplate.opsForValue().get(REDIS_REFRESH_TOKEN_PREFIX + refreshToken);
        if (cachedUserId == null) {
            throw new TokenInvalidException("Expired or revoked refresh token");
        }

        UUID userId = UUID.fromString(cachedUserId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new TokenInvalidException("User account no longer exists"));

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        // Rotate refresh token: generate new refresh token, delete old, cache new
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        long expiryMs = jwtTokenProvider.getRefreshTokenExpiryMs();

        redisTemplate.delete(REDIS_REFRESH_TOKEN_PREFIX + refreshToken);
        redisTemplate.opsForValue().set(
                REDIS_REFRESH_TOKEN_PREFIX + newRefreshToken,
                user.getId().toString(),
                Duration.ofMillis(expiryMs)
        );

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            redisTemplate.delete(REDIS_REFRESH_TOKEN_PREFIX + refreshToken);
        }
    }
}
