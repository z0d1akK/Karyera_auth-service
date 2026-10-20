
package com.zodiakk.authservice.auth.security.jwt.service;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;

import java.util.UUID;

public interface JwtService {

    String generateAccessToken(UUID userId, Role role, String email);

    String generateRefreshToken(UUID userId, Role role, String email);

    JwtTokenClaims parseToken(String token);

    JwtTokenClaims parseToken(String token, JwtTokenType expectedType);

    UUID extractUserId(String token);

    Role extractRole(String token);

    String extractEmail(String token);

    JwtTokenType extractTokenType(String token);

    boolean isTokenValid(String token);

    boolean isTokenValid(String token, JwtTokenType expectedType);
}
