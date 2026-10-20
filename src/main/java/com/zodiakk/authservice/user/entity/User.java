package com.zodiakk.authservice.user.entity;

import com.zodiakk.authservice.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(
            name = "employee_id",
            nullable = false,
            updatable = false
    )
    private UUID employeeId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Boolean active;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_status", nullable = false, length = 20)
    private AccountDeletionStatus deletionStatus = AccountDeletionStatus.NONE;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        User user = (User) o;

        if (getId() != null && user.getId() != null) {
            return Objects.equals(getId(), user.getId());
        }

        return Objects.equals(employeeId, user.employeeId);
    }

    @Override
    public final int hashCode() {
        return getId() != null
                ? Objects.hash(getId())
                : Objects.hash(employeeId);
    }
}