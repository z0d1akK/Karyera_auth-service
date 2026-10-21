package com.zodiakk.authservice.auth.security.jwt.service.impl;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.exception.ExpiredJwtTokenException;
import com.zodiakk.authservice.auth.exception.InvalidJwtTokenException;
import com.zodiakk.authservice.auth.security.jwt.JwtProperties;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JwtServiceImplTest {

    private static final String RAW_SECRET =
            "this-is-a-very-secure-secret-key-of-at-least-32-bytes-long!!";

    private static final String SECRET = Base64.getEncoder().encodeToString(RAW_SECRET.getBytes());

    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("another-very-secure-secret-key-of-at-least-32-bytes".getBytes());

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String EMAIL = "user@company.com";

    private JwtProperties properties;

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        properties = properties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7));
        jwtService = new JwtServiceImpl(properties);
        jwtService.initialize();
    }

    @Test
    @DisplayName("generates and parses an access token")
    void shouldGenerateAccessToken() {
        String token = jwtService.generateAccessToken(USER_ID, Role.HR, EMAIL);

        JwtTokenClaims claims = jwtService.parseToken(token, JwtTokenType.ACCESS);

        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.role()).isEqualTo(Role.HR);
        assertThat(claims.email()).isEqualTo(EMAIL);
        assertThat(claims.tokenType()).isEqualTo(JwtTokenType.ACCESS);
        assertThat(claims.tokenId()).isNotNull();
        assertThat(claims.issuedAt()).isNotNull();
        assertThat(Duration.between(claims.issuedAt(), claims.expiresAt()).toMinutes())
                .isCloseTo(15, within(1L));
    }

    @Test
    @DisplayName("generates and parses a refresh token")
    void shouldGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(USER_ID, Role.ADMIN, EMAIL);

        JwtTokenClaims claims = jwtService.parseToken(token, JwtTokenType.REFRESH);

        assertThat(claims.tokenType()).isEqualTo(JwtTokenType.REFRESH);
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
        assertThat(Duration.between(claims.issuedAt(), claims.expiresAt()).toDays())
                .isCloseTo(7, within(1L));
    }

    @Test
    @DisplayName("rejects a token with an unexpected type")
    void shouldRejectWrongTokenType() {
        String refreshToken = jwtService.generateRefreshToken(USER_ID, Role.EMPLOYEE, EMAIL);

        assertThatThrownBy(() -> jwtService.parseToken(refreshToken, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a null expected token type")
    void shouldRejectNullExpectedType() {
        String token = jwtService.generateAccessToken(USER_ID, Role.EMPLOYEE, EMAIL);

        assertThatThrownBy(() -> jwtService.parseToken(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token type");
    }

    @Test
    @DisplayName("rejects null and blank tokens")
    void shouldRejectNullOrBlankToken() {
        assertThatThrownBy(() -> jwtService.parseToken(null, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);

        assertThatThrownBy(() -> jwtService.parseToken("   ", JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects malformed tokens")
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> jwtService.parseToken("not-a-jwt", JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects tokens signed with another key")
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtProperties otherProperties = properties(OTHER_SECRET, Duration.ofMinutes(15), Duration.ofDays(7));
        JwtServiceImpl otherService = new JwtServiceImpl(otherProperties);
        otherService.initialize();

        String foreignToken = otherService.generateAccessToken(USER_ID, Role.EMPLOYEE, EMAIL);

        assertThatThrownBy(() -> jwtService.parseToken(foreignToken, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects an expired token")
    void shouldRejectExpiredToken() {
        String token = signToken(builder -> builder
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600))));

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(ExpiredJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token without email claim")
    void shouldRejectTokenWithoutEmail() {
        assertThatThrownBy(() -> jwtService.parseToken(
                signTokenWithoutEmail(), JwtTokenType.ACCESS
        )).isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token with an unknown role")
    void shouldRejectTokenWithUnknownRole() {
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", "SUPER_ADMIN")
                .claim("email", EMAIL)
                .claim("token_type", JwtTokenType.ACCESS.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token with an unknown token type")
    void shouldRejectTokenWithUnknownType() {
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", Role.EMPLOYEE.name())
                .claim("email", EMAIL)
                .claim("token_type", "UNKNOWN")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token with a non-uuid subject")
    void shouldRejectTokenWithInvalidSubject() {
        String token = Jwts.builder()
                .subject("not-a-uuid")
                .claim("role", Role.EMPLOYEE.name())
                .claim("email", EMAIL)
                .claim("token_type", JwtTokenType.ACCESS.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token without id")
    void shouldRejectTokenWithoutId() {
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", Role.EMPLOYEE.name())
                .claim("email", EMAIL)
                .claim("token_type", JwtTokenType.ACCESS.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token issued in the future")
    void shouldRejectTokenIssuedInTheFuture() {
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", Role.EMPLOYEE.name())
                .claim("email", EMAIL)
                .claim("token_type", JwtTokenType.ACCESS.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().plusSeconds(3600)))
                .expiration(Date.from(Instant.now().plusSeconds(7200)))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("rejects a token with expiration before issued-at")
    void shouldRejectTokenWithInvalidTimeRange() {
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", Role.EMPLOYEE.name())
                .claim("email", EMAIL)
                .claim("token_type", JwtTokenType.ACCESS.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().plusSeconds(3600)))
                .expiration(Date.from(Instant.now().plusSeconds(1800)))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(InvalidJwtTokenException.class);
    }

    @Test
    @DisplayName("requires user id, role and email to generate a token")
    void shouldValidateGenerationArguments() {
        assertThatThrownBy(() -> jwtService.generateAccessToken(null, Role.EMPLOYEE, EMAIL))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> jwtService.generateAccessToken(USER_ID, null, EMAIL))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> jwtService.generateAccessToken(USER_ID, Role.EMPLOYEE, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> jwtService.generateRefreshToken(USER_ID, Role.EMPLOYEE, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fails initialization for a non-base64 secret")
    void shouldFailOnInvalidSecret() {
        JwtServiceImpl service = new JwtServiceImpl(
                properties("!!!not-base64!!!", Duration.ofMinutes(1), Duration.ofDays(1))
        );

        assertThatThrownBy(service::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("fails initialization for a too short secret")
    void shouldFailOnTooShortSecret() {
        JwtServiceImpl service = new JwtServiceImpl(
                properties(Base64.getEncoder().encodeToString("short".getBytes()), Duration.ofMinutes(1),
                        Duration.ofDays(1))
        );

        assertThatThrownBy(service::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("fails initialization for a missing secret")
    void shouldFailOnNullSecret() {
        JwtServiceImpl service = new JwtServiceImpl(properties(null, Duration.ofMinutes(1), Duration.ofDays(1)));

        assertThatThrownBy(service::initialize)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fails initialization for non-positive expirations")
    void shouldFailOnNonPositiveExpiration() {
        JwtServiceImpl nullAccess = new JwtServiceImpl(properties(SECRET, null, Duration.ofDays(1)));
        assertThatThrownBy(nullAccess::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive duration");

        JwtServiceImpl zeroRefresh = new JwtServiceImpl(properties(SECRET, Duration.ofMinutes(1), Duration.ZERO));
        assertThatThrownBy(zeroRefresh::initialize)
                .isInstanceOf(IllegalStateException.class);

        JwtServiceImpl negativeAccess = new JwtServiceImpl(
                properties(SECRET, Duration.ofMinutes(-1), Duration.ofDays(1))
        );
        assertThatThrownBy(negativeAccess::initialize)
                .isInstanceOf(IllegalStateException.class);
    }

    private String signToken(java.util.function.UnaryOperator<io.jsonwebtoken.JwtBuilder> customizer) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", Role.EMPLOYEE.name())
                .claim("email", EMAIL)
                .claim("token_type", JwtTokenType.ACCESS.name())
                .id(UUID.randomUUID().toString());

        return customizer.apply(builder).signWith(signingKey()).compact();
    }

    private String signTokenWithoutEmail() {
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", Role.EMPLOYEE.name())
                .claim("token_type", JwtTokenType.ACCESS.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private JwtProperties properties(String secret, Duration accessExpiration, Duration refreshExpiration) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(secret);
        jwtProperties.setAccessTokenExpiration(accessExpiration);
        jwtProperties.setRefreshTokenExpiration(refreshExpiration);
        return jwtProperties;
    }
}
