package com.zodiakk.authservice.auth.service.impl;

import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Test
    @DisplayName("deletes refresh tokens that expired before the current UTC time")
    void shouldDeleteExpiredTokens() {
        when(refreshTokenRepository.deleteExpiredTokens(any(OffsetDateTime.class))).thenReturn(3);

        assertThatCode(() -> refreshTokenService.cleanupExpiredTokens()).doesNotThrowAnyException();

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(refreshTokenRepository).deleteExpiredTokens(captor.capture());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertThat(captor.getValue()).isBetween(now.minusSeconds(5), now.plusSeconds(5));
    }

    @Test
    @DisplayName("completes when there is nothing to delete")
    void shouldCompleteWhenNothingToDelete() {
        when(refreshTokenRepository.deleteExpiredTokens(any(OffsetDateTime.class))).thenReturn(0);

        assertThatCode(() -> refreshTokenService.cleanupExpiredTokens()).doesNotThrowAnyException();
    }
}
