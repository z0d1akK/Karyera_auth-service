package com.zodiakk.authservice.auth.repository;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {

    Optional<AuthCredential> findByLogin(String login);

    Optional<AuthCredential> findByUserId(UUID userId);

    boolean existsByLogin(String login);

    boolean existsByUserId(UUID userId);
}