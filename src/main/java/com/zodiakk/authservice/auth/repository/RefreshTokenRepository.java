package com.zodiakk.authservice.auth.repository;

import com.zodiakk.authservice.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rt
            FROM RefreshToken rt
            WHERE rt.token = :token
            """)
    Optional<RefreshToken> findByTokenForUpdate(
            @Param("token") String token
    );

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true
            WHERE rt.userId = :userId
              AND rt.revoked = false
            """)
    int revokeAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken rt
            WHERE rt.expiresAt < :now
            """)
    int deleteExpiredTokens(@Param("now") OffsetDateTime now);

    void deleteAllByUserId(UUID userId);
}