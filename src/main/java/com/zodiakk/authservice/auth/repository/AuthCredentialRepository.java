package com.zodiakk.authservice.auth.repository;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.auth.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {

    Optional<AuthCredential> findByLogin(String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AuthCredential c WHERE c.login = :login")
    Optional<AuthCredential> findByLoginForUpdate(@Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AuthCredential c where c.userId = :userId")
    Optional<AuthCredential> findByUserIdForUpdate(@Param("userId") UUID userId);

    Optional<AuthCredential> findByUserId(UUID userId);

    List<AuthCredential> findAllByUserIdIn(Collection<UUID> userIds);

    @Query("""
    SELECT COUNT(c)
    FROM AuthCredential c, User u
    WHERE c.userId = u.id
      AND c.role = :role
      AND u.active = true
    """)
    long countActiveUsersByRole(@Param("role") Role role);

    void deleteByUserId(UUID userId);
}