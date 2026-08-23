package com.finagent.authservice;

import com.finagent.authservice.dto.*;
import com.finagent.authservice.exception.UserAlreadyExistsException;
import com.finagent.authservice.model.Role;
import com.finagent.authservice.model.User;
import com.finagent.authservice.repository.UserRepository;
import com.finagent.authservice.security.JwtTokenProvider;
import com.finagent.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
public class AuthServiceTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testUserRegistrationSuccess() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123", Role.CUSTOMER);
        User user = authService.register(request);

        assertNotNull(user.getId());
        assertEquals("Test User", user.getName());
        assertEquals("test@example.com", user.getEmail());
        assertTrue(passwordEncoder.matches("password123", user.getPasswordHash()));
        assertEquals(Role.CUSTOMER, user.getRole());
    }

    @Test
    public void testUserRegistrationDuplicateEmailThrowsConflict() {
        RegisterRequest request = new RegisterRequest("Test User", "duplicate@example.com", "password123", Role.CUSTOMER);
        authService.register(request);

        // Register second time with duplicate email
        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
    }

    @Test
    public void testUserLoginSuccess() {
        // Register user first
        RegisterRequest regRequest = new RegisterRequest("Login User", "login@example.com", "secretPass", Role.ADMIN);
        User registeredUser = authService.register(regRequest);

        LoginRequest loginRequest = new LoginRequest("login@example.com", "secretPass");
        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals(registeredUser.getId(), response.getUserId());
        assertEquals(Role.ADMIN, response.getRole());

        // Verify refresh token is cached in Redis
        verify(valueOperations).set(
                eq("rt:" + response.getRefreshToken()),
                eq(registeredUser.getId().toString()),
                any()
        );
    }

    @Test
    public void testUserLoginInvalidCredentialsThrowsUnauthorized() {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "wrongpass");
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
    }

    @Test
    public void testTokenRefreshSuccess() {
        RegisterRequest regRequest = new RegisterRequest("Refresh User", "refresh@example.com", "pass123", Role.LOAN_OFFICER);
        User user = authService.register(regRequest);

        // Generate token and mock Redis get
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        when(valueOperations.get("rt:" + refreshToken)).thenReturn(user.getId().toString());

        TokenRefreshRequest refreshRequest = new TokenRefreshRequest(refreshToken);
        TokenRefreshResponse response = authService.refresh(refreshRequest);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());

        // Verify old refresh token is removed and new refresh token is stored
        verify(redisTemplate).delete("rt:" + refreshToken);
        verify(valueOperations).set(
                eq("rt:" + response.getRefreshToken()),
                eq(user.getId().toString()),
                any()
        );
    }
}
