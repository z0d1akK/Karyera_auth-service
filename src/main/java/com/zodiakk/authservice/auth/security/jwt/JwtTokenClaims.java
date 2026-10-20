
package com.zodiakk.authservice.auth.security.jwt;

import com.zodiakk.authservice.auth.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record JwtTokenClaims(
        UUID userId,
        Role role,
        String email,
        JwtTokenType tokenType,
        UUID tokenId,
        Instant issuedAt,
        Instant expiresAt
) {
}