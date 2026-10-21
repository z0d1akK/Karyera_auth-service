package com.zodiakk.authservice.common;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.auth.security.jwt.service.JwtService;
import com.zodiakk.authservice.config.TestcontainersConfiguration;
import com.zodiakk.authservice.integration.employee.client.EmployeeClient;
import com.zodiakk.authservice.integration.employee.repository.ProcessedEventRepository;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected EmployeeClient employeeClient;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AuthCredentialRepository authCredentialRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected OutboxEventRepository outboxEventRepository;

    @Autowired
    protected ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE processed_events, outbox_events, refresh_tokens, auth_credentials, users CASCADE
                """);
    }

    protected String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    protected User persistUser(String email, Role role, String rawPassword, boolean passwordChangeRequired) {
        return persistUser(email, role, rawPassword, passwordChangeRequired, UUID.randomUUID(), true,
                AccountDeletionStatus.NONE);
    }

    protected User persistUser(
            String email,
            Role role,
            String rawPassword,
            boolean passwordChangeRequired,
            UUID employeeId,
            boolean active,
            AccountDeletionStatus deletionStatus
    ) {
        User user = userRepository.saveAndFlush(User.builder()
                .employeeId(employeeId)
                .email(email)
                .active(active)
                .deletionStatus(deletionStatus)
                .build());

        authCredentialRepository.saveAndFlush(AuthCredential.builder()
                .userId(user.getId())
                .login(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .passwordChangeRequired(passwordChangeRequired)
                .build());

        return user;
    }

    protected AuthCredential credentialOf(User user) {
        return authCredentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Credential is missing for user " + user.getId()));
    }

    protected String accessToken(User user) {
        AuthCredential credential = credentialOf(user);
        return jwtService.generateAccessToken(user.getId(), credential.getRole(), user.getEmail());
    }

    protected String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}
