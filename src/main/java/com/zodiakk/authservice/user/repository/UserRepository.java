package com.zodiakk.authservice.user.repository;

import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletionStatusNot(UUID id, AccountDeletionStatus deletionStatus);

    Page<User> findAllByDeletionStatusNot(AccountDeletionStatus deletionStatus, Pageable pageable);

    Optional<User> findByEmployeeIdAndDeletionStatusNot(UUID employeeId, AccountDeletionStatus status);

    boolean existsByEmployeeIdAndDeletionStatusNot(UUID employeeId, AccountDeletionStatus status);

    boolean existsByEmailIgnoreCaseAndDeletionStatusNot(String email, AccountDeletionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM User u
        WHERE u.id = :userId
        """)
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

    @Query("""
        SELECT u
        FROM User u
        WHERE u.deletionStatus = :status
          AND u.updatedAt < :threshold
        ORDER BY u.updatedAt
        """)
    List<User> findStuckDeletions(
            @Param("status") AccountDeletionStatus status,
            @Param("threshold") OffsetDateTime threshold,
            Pageable pageable
    );
}