package com.zodiakk.authservice.registration.entity;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "registration_requests")
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class RegistrationRequest extends BaseEntity {

    @Column(name = "employee_id", nullable = false, unique = true)
    private UUID employeeId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RegistrationRequestStatus status;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RegistrationRequest request = (RegistrationRequest) o;

        if (getId() != null && request.getId() != null) {
            return Objects.equals(getId(), request.getId());
        }

        return Objects.equals(employeeId, request.employeeId);
    }

    @Override
    public final int hashCode() {
        return getId() != null
                ? Objects.hash(getId())
                : Objects.hash(employeeId);
    }
}