package com.zodiakk.authservice.auth.security.jwt.service.impl;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.exception.ExpiredJwtTokenException;
import com.zodiakk.authservice.auth.exception.InvalidJwtTokenException;
import com.zodiakk.authservice.auth.security.jwt.JwtProperties;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.auth.security.jwt.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String EMAIL_CLAIM = "email";
    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey;

    @PostConstruct
    void initialize() {
        validateExpiration(jwtProperties.getAccessTokenExpiration());
        validateExpiration(jwtProperties.getRefreshTokenExpiration());

        try {
            byte[] keyBytes = Decoders.BASE64.decode(
                    jwtProperties.getSecret()
            );

            signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "JWT secret must be a valid Base64-encoded signing key",
                    ex
            );
        }
    }

    @Override
    public String generateAccessToken(UUID userId, Role role, String email) {
        return generateToken(
                userId,
                role,
                email,
                JwtTokenType.ACCESS,
                jwtProperties.getAccessTokenExpiration()
        );
    }

    @Override
    public String generateRefreshToken(UUID userId, Role role, String email) {
        return generateToken(
                userId,
                role,
                email,
                JwtTokenType.REFRESH,
                jwtProperties.getRefreshTokenExpiration()
        );
    }

    private String generateToken(
            UUID userId,
            Role role,
            String email,
            JwtTokenType tokenType,
            Duration expiration
    ) {
        if (userId == null || role == null
                || email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "User ID, role and email are required"
            );
        }

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role.name())
                .claim(EMAIL_CLAIM, email)
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public JwtTokenClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidJwtTokenException();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return mapClaims(claims);

        } catch (ExpiredJwtException ex) {
            throw new ExpiredJwtTokenException();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidJwtTokenException();
        }
    }

    @Override
    public JwtTokenClaims parseToken(
            String token,
            JwtTokenType expectedType
    ) {
        if (expectedType == null) {
            throw new IllegalArgumentException(
                    "Expected JWT token type is required"
            );
        }

        JwtTokenClaims claims = parseToken(token);

        if (claims.tokenType() != expectedType) {
            throw new InvalidJwtTokenException();
        }

        return claims;
    }

    @Override
    public UUID extractUserId(String token) {
        return parseToken(token).userId();
    }

    @Override
    public Role extractRole(String token) {
        return parseToken(token).role();
    }

    @Override
    public String extractEmail(String token) {
        return parseToken(token).email();
    }

    @Override
    public JwtTokenType extractTokenType(String token) {
        return parseToken(token).tokenType();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtTokenException | InvalidJwtTokenException ex) {
            return false;
        }
    }

    @Override
    public boolean isTokenValid(
            String token,
            JwtTokenType expectedType
    ) {
        if (expectedType == null) {
            return false;
        }

        try {
            parseToken(token, expectedType);
            return true;
        } catch (ExpiredJwtTokenException | InvalidJwtTokenException ex) {
            return false;
        }
    }

    private JwtTokenClaims mapClaims(Claims claims) {
        try {
            UUID userId = UUID.fromString(claims.getSubject());

            String roleValue = claims.get(ROLE_CLAIM, String.class);
            Role role = Role.valueOf(roleValue);

            String email = claims.get(EMAIL_CLAIM, String.class);
            if (email == null || email.isBlank()) {
                throw new InvalidJwtTokenException();
            }

            String typeValue = claims.get(
                    TOKEN_TYPE_CLAIM,
                    String.class
            );
            JwtTokenType tokenType = JwtTokenType.valueOf(typeValue);

            UUID tokenId = UUID.fromString(claims.getId());

            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();

            if (issuedAt == null || expiresAt == null
                    || !issuedAt.before(expiresAt)
                    || issuedAt.toInstant().isAfter(Instant.now())) {
                throw new InvalidJwtTokenException();
            }

            return new JwtTokenClaims(
                    userId,
                    role,
                    email,
                    tokenType,
                    tokenId,
                    issuedAt.toInstant(),
                    expiresAt.toInstant()
            );

        } catch (JwtException
                 | IllegalArgumentException
                 | NullPointerException ex) {
            throw new InvalidJwtTokenException();
        }
    }

    private void validateExpiration(Duration expiration) {
        if (expiration == null
                || expiration.isZero()
                || expiration.isNegative()) {
            throw new IllegalStateException(
                    "JWT expiration must be a positive duration"
            );
        }
    }
}
