package com.zodiakk.authservice.auth.service.impl;

import com.zodiakk.authservice.auth.dto.request.ChangeInitialPasswordRequestDto;
import com.zodiakk.authservice.auth.dto.request.LoginRequestDto;
import com.zodiakk.authservice.auth.dto.request.RefreshTokenRequestDto;
import com.zodiakk.authservice.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.authservice.auth.dto.response.TokenResponseDto;
import com.zodiakk.authservice.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.RefreshToken;
import com.zodiakk.authservice.auth.exception.*;
import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.auth.security.jwt.service.JwtService;
import com.zodiakk.authservice.auth.security.utils.SecurityContextUtils;
import com.zodiakk.authservice.auth.service.AuthService;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthCredentialRepository authCredentialRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    @Override
    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {

        String login = normalizeLogin(request.getLogin());

        AuthCredential credential = authCredentialRepository.findByLogin(login)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        User user = findActiveUser(credential.getUserId(), InvalidCredentialsException::new);

        if (Boolean.TRUE.equals(credential.getPasswordChangeRequired())) {
            throw new PasswordChangeRequiredException();
        }

        return generateTokenResponse(credential, user);
    }

    @Override
    @Transactional
    public TokenResponseDto refreshToken(RefreshTokenRequestDto request) {

        String token = request.getRefreshToken();

        RefreshToken storedToken = findActiveRefreshTokenForUpdate(token);
        JwtTokenClaims claims = parseRefreshToken(token);

        if (!storedToken.getUserId().equals(claims.userId())) {
            throw new InvalidRefreshTokenException();
        }

        AuthCredential credential = authCredentialRepository
                .findByUserId(claims.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = findActiveUser(claims.userId(), InvalidRefreshTokenException::new);

        if (Boolean.TRUE.equals(credential.getPasswordChangeRequired())) {
            throw new InvalidRefreshTokenException();
        }

        storedToken.setRevoked(true);

        return generateTokenResponse(credential, user);
    }

    @Override
    public TokenValidationResponseDto validateToken(ValidateTokenRequestDto request) {

        JwtTokenClaims claims;

        try {
            claims = jwtService.parseToken(request.getToken(), JwtTokenType.ACCESS);
        } catch (ExpiredJwtTokenException | InvalidJwtTokenException ex) {
            return TokenValidationResponseDto.builder()
                    .valid(false)
                    .build();
        }

        User user = userRepository.findById(claims.userId())
                .orElse(null);

        if (user == null
                || !Boolean.TRUE.equals(user.getActive())
                || user.getDeletionStatus() != AccountDeletionStatus.NONE) {
            return TokenValidationResponseDto.builder()
                    .valid(false)
                    .build();
        }

        AuthCredential credential = authCredentialRepository
                .findByUserId(user.getId())
                .orElse(null);

        if (credential == null) {
            return TokenValidationResponseDto.builder()
                    .valid(false)
                    .build();
        }

        if (credential.getRole() != claims.role()
                || !user.getEmail().equals(claims.email())) {
            return TokenValidationResponseDto.builder()
                    .valid(false)
                    .build();
        }

        if (Boolean.TRUE.equals(credential.getPasswordChangeRequired())) {
            return TokenValidationResponseDto.builder()
                    .valid(false)
                    .build();
        }

        return TokenValidationResponseDto.builder()
                .valid(true)
                .userId(user.getId())
                .role(credential.getRole())
                .email(user.getEmail())
                .build();
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequestDto request) {

        UUID currentUserId = SecurityContextUtils.getCurrentUserId();

        RefreshToken storedToken = findActiveRefreshTokenForUpdate(request.getRefreshToken());

        JwtTokenClaims claims = parseRefreshToken(request.getRefreshToken());

        if (!storedToken.getUserId().equals(claims.userId()) || !currentUserId.equals(claims.userId())) {
            throw new InvalidRefreshTokenException();
        }

        storedToken.setRevoked(true);
    }

    @Override
    @Transactional
    public void changeInitialPassword(ChangeInitialPasswordRequestDto request) {
        String login = normalizeLogin(request.getLogin());

        AuthCredential credential = authCredentialRepository
                .findByLoginForUpdate(login)
                .orElseThrow(InvalidCredentialsException::new);

        User user = findActiveUser(
                credential.getUserId(),
                InvalidCredentialsException::new
        );

        if (!Boolean.TRUE.equals(credential.getPasswordChangeRequired())) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(
                request.getInitialPassword(),
                credential.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        if (passwordEncoder.matches(
                request.getNewPassword(),
                credential.getPasswordHash()
        )) {
            throw new PasswordMustDifferException();
        }

        credential.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword())
        );
        credential.setPasswordChangeRequired(false);

        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private TokenResponseDto generateTokenResponse(AuthCredential credential, User user) {

        UUID userId = user.getId();

        String accessToken = jwtService.generateAccessToken(
                userId,
                credential.getRole(),
                user.getEmail()
        );

        String refreshTokenValue = jwtService.generateRefreshToken(
                userId,
                credential.getRole(),
                user.getEmail()
        );

        JwtTokenClaims refreshClaims = jwtService.parseToken(refreshTokenValue, JwtTokenType.REFRESH);

        OffsetDateTime expiresAt = refreshClaims.expiresAt().atOffset(ZoneOffset.UTC);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .token(refreshTokenValue)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    private <X extends RuntimeException> User findActiveUser(UUID userId, Supplier<X> exceptionSupplier) {

        User user = userRepository.findById(userId)
                .orElseThrow(exceptionSupplier);

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw exceptionSupplier.get();
        }

        if (user.getDeletionStatus() != AccountDeletionStatus.NONE) {
            throw exceptionSupplier.get();
        }

        return user;
    }

    private String normalizeLogin(String login) {
        return login == null ? null : login.trim().toLowerCase(Locale.ROOT);
    }

    private JwtTokenClaims parseRefreshToken(String token) {
        try {
            return jwtService.parseToken(token, JwtTokenType.REFRESH);
        } catch (ExpiredJwtTokenException | InvalidJwtTokenException ex) {
            throw new InvalidRefreshTokenException();
        }
    }

    private RefreshToken findActiveRefreshTokenForUpdate(String token) {
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenForUpdate(token)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!storedToken.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        return storedToken;
    }

}