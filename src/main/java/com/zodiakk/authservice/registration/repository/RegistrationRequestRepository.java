package com.zodiakk.authservice.registration.repository;

import com.zodiakk.authservice.registration.entity.RegistrationRequest;
import com.zodiakk.authservice.registration.entity.RegistrationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    Optional<RegistrationRequest> findByEmployeeId(UUID employeeId);

    Optional<RegistrationRequest> findByEmail(String email);

    List<RegistrationRequest> findAllByStatus(RegistrationRequestStatus status);

    boolean existsByEmployeeIdAndStatus(UUID employeeId, RegistrationRequestStatus status);

    boolean existsByEmailAndStatus(String email, RegistrationRequestStatus status);
}