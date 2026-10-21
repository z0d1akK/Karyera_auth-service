package com.zodiakk.authservice.user.security;

import com.zodiakk.authservice.auth.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAdministrationPolicyTest {

    private final UserAdministrationPolicy policy = new UserAdministrationPolicy();

    @Test
    @DisplayName("ADMIN can create accounts with any role")
    void adminCanCreateAnyRole() {
        for (Role targetRole : Role.values()) {
            assertThatCode(() -> policy.checkCreatePermission(Role.ADMIN, targetRole))
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"EMPLOYEE", "MANAGER"})
    @DisplayName("HR can create EMPLOYEE and MANAGER accounts")
    void hrCanCreateEmployeeAndManager(Role targetRole) {
        assertThatCode(() -> policy.checkCreatePermission(Role.HR, targetRole))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"HR", "ADMIN"})
    @DisplayName("HR cannot create HR and ADMIN accounts")
    void hrCannotCreatePrivilegedRoles(Role targetRole) {
        assertThatThrownBy(() -> policy.checkCreatePermission(Role.HR, targetRole))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"EMPLOYEE", "MANAGER"})
    @DisplayName("Employees and managers cannot create accounts")
    void nonAdministratorsCannotCreate(Role operatorRole) {
        assertThatThrownBy(() -> policy.checkCreatePermission(operatorRole, Role.EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Null operator role is rejected")
    void nullOperatorCannotCreate() {
        assertThatThrownBy(() -> policy.checkCreatePermission(null, Role.EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Only ADMIN can update accounts")
    void onlyAdminCanUpdate() {
        assertThatCode(() -> policy.checkUpdatePermission(Role.ADMIN)).doesNotThrowAnyException();

        for (Role role : new Role[]{Role.HR, Role.MANAGER, Role.EMPLOYEE, null}) {
            assertThatThrownBy(() -> policy.checkUpdatePermission(role))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
