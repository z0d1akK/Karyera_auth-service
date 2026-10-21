package com.zodiakk.authservice.auth.service.impl;

import com.zodiakk.authservice.auth.dto.request.ChangeInitialPasswordRequestDto;
import com.zodiakk.authservice.auth.dto.request.LoginRequestDto;
import com.zodiakk.authservice.auth.dto.request.RefreshTokenRequestDto;
import com.zodiakk.authservice.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.authservice.auth.dto.response.TokenResponseDto;
import com.zodiakk.authservice.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.RefreshToken;
import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.exception.ExpiredJwtTokenException;
import com.zodiakk.authservice.auth.exception.InvalidCredentialsException;
import com.zodiakk.authservice.auth.exception.InvalidJwtTokenException;
import com.zodiakk.authservice.auth.exception.InvalidRefreshTokenException;
import com.zodiakk.authservice.auth.exception.PasswordChangeRequiredException;
import com.zodiakk.authservice.auth.exception.PasswordMustDifferException;
import com.zodiakk.authservice.auth.repository.AuthCredentialRepository;
import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.auth.security.jwt.principal.AuthenticatedUser;
import com.zodiakk.authservice.auth.security.jwt.service.JwtService;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthCredentialRepository authCredentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private final JwtTokenClaims refreshTokenClaims = AuthTestDataFactory.claims();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns token pair and stores a refresh token for valid credentials")
        void shouldReturnTokensForValidCredentials() {
            LoginRequestDto request = AuthTestDataFactory.loginRequest();
            AuthCredential credential = AuthTestDataFactory.credential();
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            stubTokenGeneration();

            TokenResponseDto response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo(AuthTestDataFactory.ACCESS_TOKEN);
            assertThat(response.getRefreshToken()).isEqualTo(AuthTestDataFactory.REFRESH_TOKEN);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken savedToken = captor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(AuthTestDataFactory.USER_ID);
            assertThat(savedToken.getToken()).isEqualTo(AuthTestDataFactory.REFRESH_TOKEN);
            assertThat(savedToken.getRevoked()).isFalse();
            assertThat(savedToken.getExpiresAt()).isEqualTo(
                    refreshTokenClaims.expiresAt().atOffset(ZoneOffset.UTC)
            );
        }

        @Test
        @DisplayName("normalizes login before searching for credentials")
        void shouldNormalizeLogin() {
            LoginRequestDto request = LoginRequestDto.builder()
                    .login("  User@Company.COM  ")
                    .password(AuthTestDataFactory.RAW_PASSWORD)
                    .build();
            AuthCredential credential = AuthTestDataFactory.credential();
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            stubTokenGeneration();

            authService.login(request);

            verify(authCredentialRepository).findByLogin(AuthTestDataFactory.EMAIL);
        }

        @Test
        @DisplayName("rejects unknown login")
        void shouldRejectUnknownLogin() {
            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(AuthTestDataFactory.loginRequest()))
                    .isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(jwtService);
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects null login")
        void shouldRejectNullLogin() {
            LoginRequestDto request = LoginRequestDto.builder().login(null).password("password").build();

            when(authCredentialRepository.findByLogin(isNull())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects wrong password")
        void shouldRejectWrongPassword() {
            AuthCredential credential = AuthTestDataFactory.credential();

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(false);

            assertThatThrownBy(() -> authService.login(AuthTestDataFactory.loginRequest()))
                    .isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("rejects login when user does not exist")
        void shouldRejectMissingUser() {
            AuthCredential credential = AuthTestDataFactory.credential();

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(AuthTestDataFactory.loginRequest()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects inactive user")
        void shouldRejectInactiveUser() {
            AuthCredential credential = AuthTestDataFactory.credential();
            User inactiveUser = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.NONE
            );

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.login(AuthTestDataFactory.loginRequest()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects user that is being deleted")
        void shouldRejectUserBeingDeleted() {
            AuthCredential credential = AuthTestDataFactory.credential();
            User deletingUser = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    true,
                    AccountDeletionStatus.DELETING
            );

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(deletingUser));

            assertThatThrownBy(() -> authService.login(AuthTestDataFactory.loginRequest()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("requires initial password change")
        void shouldRequirePasswordChange() {
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLogin(AuthTestDataFactory.EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(AuthTestDataFactory.loginRequest()))
                    .isInstanceOf(PasswordChangeRequiredException.class);

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenFlow {

        @Test
        @DisplayName("rotates tokens and revokes the used refresh token")
        void shouldRotateTokens() {
            RefreshTokenRequestDto request = AuthTestDataFactory.refreshTokenRequest();
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();
            AuthCredential credential = AuthTestDataFactory.credential();
            User user = AuthTestDataFactory.user();

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.EMAIL,
                            JwtTokenType.REFRESH
                    ));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            stubTokenGeneration();

            TokenResponseDto response = authService.refreshToken(request);

            assertThat(response.getAccessToken()).isEqualTo(AuthTestDataFactory.ACCESS_TOKEN);
            assertThat(response.getRefreshToken()).isEqualTo(AuthTestDataFactory.REFRESH_TOKEN);
            assertThat(storedToken.getRevoked()).isTrue();

            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("rejects unknown refresh token")
        void shouldRejectUnknownToken() {
            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("rejects revoked refresh token")
        void shouldRejectRevokedToken() {
            RefreshToken revoked = AuthTestDataFactory.refreshToken(
                    AuthTestDataFactory.REFRESH_TOKEN,
                    AuthTestDataFactory.USER_ID,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                    true
            );

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects expired refresh token")
        void shouldRejectExpiredToken() {
            RefreshToken expired = AuthTestDataFactory.refreshToken(
                    AuthTestDataFactory.REFRESH_TOKEN,
                    AuthTestDataFactory.USER_ID,
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                    false
            );

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects refresh token that belongs to another user")
        void shouldRejectTokenOfAnotherUser() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.OTHER_USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.OTHER_EMAIL,
                            JwtTokenType.REFRESH
                    ));

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects malformed refresh token")
        void shouldRejectMalformedToken() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenThrow(new InvalidJwtTokenException());

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects expired jwt refresh token")
        void shouldRejectExpiredJwtToken() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenThrow(new ExpiredJwtTokenException());

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects refresh token when credentials are missing")
        void shouldRejectMissingCredential() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.EMAIL,
                            JwtTokenType.REFRESH
                    ));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects refresh token when user is inactive")
        void shouldRejectInactiveUser() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();
            AuthCredential credential = AuthTestDataFactory.credential();
            User inactiveUser = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.NONE
            );

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.EMAIL,
                            JwtTokenType.REFRESH
                    ));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects refresh token when password change is required")
        void shouldRejectWhenPasswordChangeRequired() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );
            User user = AuthTestDataFactory.user();

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.EMAIL,
                            JwtTokenType.REFRESH
                    ));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.refreshToken(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("returns metadata for a valid access token")
        void shouldValidateToken() {
            ValidateTokenRequestDto request = AuthTestDataFactory.validateTokenRequest();
            AuthCredential credential = AuthTestDataFactory.credential();
            User user = AuthTestDataFactory.user();

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));

            TokenValidationResponseDto response = authService.validateToken(request);

            assertThat(response.getValid()).isTrue();
            assertThat(response.getUserId()).isEqualTo(AuthTestDataFactory.USER_ID);
            assertThat(response.getRole()).isEqualTo(Role.EMPLOYEE);
            assertThat(response.getEmail()).isEqualTo(AuthTestDataFactory.EMAIL);
        }

        @Test
        @DisplayName("returns invalid result for an invalid token")
        void shouldReturnInvalidForInvalidToken() {
            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenThrow(new InvalidJwtTokenException());

            TokenValidationResponseDto response = authService.validateToken(
                    AuthTestDataFactory.validateTokenRequest()
            );

            assertThat(response.getValid()).isFalse();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("returns invalid result for an expired token")
        void shouldReturnInvalidForExpiredToken() {
            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenThrow(new ExpiredJwtTokenException());

            TokenValidationResponseDto response = authService.validateToken(
                    AuthTestDataFactory.validateTokenRequest()
            );

            assertThat(response.getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result when user does not exist")
        void shouldReturnInvalidWhenUserMissing() {
            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result for inactive user")
        void shouldReturnInvalidForInactiveUser() {
            User inactiveUser = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    false,
                    AccountDeletionStatus.NONE
            );

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(inactiveUser));

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result for user being deleted")
        void shouldReturnInvalidForDeletedUser() {
            User deletingUser = AuthTestDataFactory.user(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMPLOYEE_ID,
                    AuthTestDataFactory.EMAIL,
                    true,
                    AccountDeletionStatus.DELETING
            );

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(deletingUser));

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result when credentials are missing")
        void shouldReturnInvalidWhenCredentialMissing() {
            User user = AuthTestDataFactory.user();

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result when token role differs from stored role")
        void shouldReturnInvalidOnRoleMismatch() {
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.ADMIN,
                    false
            );

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result when token email differs from stored email")
        void shouldReturnInvalidOnEmailMismatch() {
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential();

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.OTHER_EMAIL,
                            JwtTokenType.ACCESS
                    ));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }

        @Test
        @DisplayName("returns invalid result when password change is required")
        void shouldReturnInvalidWhenPasswordChangeRequired() {
            User user = AuthTestDataFactory.user();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );

            when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                    .thenReturn(AuthTestDataFactory.claims());
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(authCredentialRepository.findByUserId(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(credential));

            assertThat(authService.validateToken(AuthTestDataFactory.validateTokenRequest()).getValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("revokes the current user refresh token")
        void shouldRevokeRefreshToken() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();
            authenticate(AuthTestDataFactory.USER_ID);

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.EMAIL,
                            JwtTokenType.REFRESH
                    ));

            assertThatCode(() -> authService.logout(AuthTestDataFactory.refreshTokenRequest()))
                    .doesNotThrowAnyException();

            assertThat(storedToken.getRevoked()).isTrue();
        }

        @Test
        @DisplayName("rejects unknown refresh token")
        void shouldRejectUnknownToken() {
            authenticate(AuthTestDataFactory.USER_ID);

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.logout(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("rejects refresh token that belongs to another user")
        void shouldRejectTokenOfAnotherUser() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();
            authenticate(AuthTestDataFactory.USER_ID);

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.OTHER_USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.OTHER_EMAIL,
                            JwtTokenType.REFRESH
                    ));

            assertThatThrownBy(() -> authService.logout(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            assertThat(storedToken.getRevoked()).isFalse();
        }

        @Test
        @DisplayName("rejects refresh token of the current user supplied by another user")
        void shouldRejectTokenWhenCurrentUserDiffers() {
            RefreshToken storedToken = AuthTestDataFactory.refreshToken();
            authenticate(AuthTestDataFactory.OTHER_USER_ID);

            when(refreshTokenRepository.findByTokenForUpdate(AuthTestDataFactory.REFRESH_TOKEN))
                    .thenReturn(Optional.of(storedToken));
            when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                    .thenReturn(AuthTestDataFactory.claims(
                            AuthTestDataFactory.USER_ID,
                            Role.EMPLOYEE,
                            AuthTestDataFactory.EMAIL,
                            JwtTokenType.REFRESH
                    ));

            assertThatThrownBy(() -> authService.logout(AuthTestDataFactory.refreshTokenRequest()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }
    }

    @Nested
    @DisplayName("changeInitialPassword")
    class ChangeInitialPassword {

        @Test
        @DisplayName("replaces the initial password and revokes sessions")
        void shouldChangePassword() {
            ChangeInitialPasswordRequestDto request = AuthTestDataFactory.changeInitialPasswordRequest();
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(passwordEncoder.matches(AuthTestDataFactory.NEW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(false);
            when(passwordEncoder.encode(AuthTestDataFactory.NEW_PASSWORD)).thenReturn("new-encoded-password");

            authService.changeInitialPassword(request);

            assertThat(credential.getPasswordHash()).isEqualTo("new-encoded-password");
            assertThat(credential.getPasswordChangeRequired()).isFalse();
            verify(refreshTokenRepository).revokeAllByUserId(AuthTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("rejects unknown login")
        void shouldRejectUnknownLogin() {
            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.changeInitialPassword(
                    AuthTestDataFactory.changeInitialPasswordRequest()
            )).isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects missing user")
        void shouldRejectMissingUser() {
            AuthCredential credential = AuthTestDataFactory.credential();

            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.changeInitialPassword(
                    AuthTestDataFactory.changeInitialPasswordRequest()
            )).isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects password change when it is not required")
        void shouldRejectWhenChangeIsNotRequired() {
            AuthCredential credential = AuthTestDataFactory.credential();
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.changeInitialPassword(
                    AuthTestDataFactory.changeInitialPasswordRequest()
            )).isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("rejects wrong initial password")
        void shouldRejectWrongInitialPassword() {
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(false);

            assertThatThrownBy(() -> authService.changeInitialPassword(
                    AuthTestDataFactory.changeInitialPasswordRequest()
            )).isInstanceOf(InvalidCredentialsException.class);

            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        @DisplayName("rejects new password that equals the current one")
        void shouldRejectSamePassword() {
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );
            User user = AuthTestDataFactory.user();

            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(passwordEncoder.matches(AuthTestDataFactory.NEW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);

            assertThatThrownBy(() -> authService.changeInitialPassword(
                    AuthTestDataFactory.changeInitialPasswordRequest()
            )).isInstanceOf(PasswordMustDifferException.class);

            verify(passwordEncoder, never()).encode(any());
            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        @DisplayName("normalizes login before locking credentials")
        void shouldNormalizeLogin() {
            AuthCredential credential = AuthTestDataFactory.credential(
                    AuthTestDataFactory.USER_ID,
                    AuthTestDataFactory.EMAIL,
                    Role.EMPLOYEE,
                    true
            );
            User user = AuthTestDataFactory.user();
            ChangeInitialPasswordRequestDto request = ChangeInitialPasswordRequestDto.builder()
                    .login("  USER@COMPANY.COM ")
                    .initialPassword(AuthTestDataFactory.RAW_PASSWORD)
                    .newPassword(AuthTestDataFactory.NEW_PASSWORD)
                    .build();

            when(authCredentialRepository.findByLoginForUpdate(AuthTestDataFactory.EMAIL))
                    .thenReturn(Optional.of(credential));
            when(userRepository.findById(AuthTestDataFactory.USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(AuthTestDataFactory.RAW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(true);
            when(passwordEncoder.matches(AuthTestDataFactory.NEW_PASSWORD, AuthTestDataFactory.ENCODED_PASSWORD))
                    .thenReturn(false);
            when(passwordEncoder.encode(AuthTestDataFactory.NEW_PASSWORD)).thenReturn("new-encoded-password");

            authService.changeInitialPassword(request);

            verify(authCredentialRepository).findByLoginForUpdate(AuthTestDataFactory.EMAIL);
        }
    }

    private void stubTokenGeneration() {
        when(jwtService.generateAccessToken(
                AuthTestDataFactory.USER_ID,
                Role.EMPLOYEE,
                AuthTestDataFactory.EMAIL
        )).thenReturn(AuthTestDataFactory.ACCESS_TOKEN);
        when(jwtService.generateRefreshToken(
                AuthTestDataFactory.USER_ID,
                Role.EMPLOYEE,
                AuthTestDataFactory.EMAIL
        )).thenReturn(AuthTestDataFactory.REFRESH_TOKEN);
        when(jwtService.parseToken(AuthTestDataFactory.REFRESH_TOKEN, JwtTokenType.REFRESH))
                .thenReturn(refreshTokenClaims);
    }

    private void authenticate(UUID userId) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .email(AuthTestDataFactory.EMAIL)
                .role(Role.EMPLOYEE)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }
}
