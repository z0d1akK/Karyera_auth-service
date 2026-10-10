package com.zodiakk.authservice.user.repository;

import com.zodiakk.authservice.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmployeeId(UUID employeeId);

    Optional<User> findByEmail(String email);

    boolean existsByEmployeeId(UUID employeeId);

    boolean existsByEmail(String email);
}