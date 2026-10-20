
package com.zodiakk.authservice.auth.service.impl;

import com.zodiakk.authservice.auth.repository.RefreshTokenRepository;
import com.zodiakk.authservice.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    @Scheduled(cron = "${refresh-token.cleanup-cron}", zone = "UTC")
    public void cleanupExpiredTokens() {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        int deletedCount = refreshTokenRepository.deleteExpiredTokens(now);

        log.info(
                "Expired refresh tokens cleanup completed. Deleted tokens count: {}",
                deletedCount
        );
    }
}
