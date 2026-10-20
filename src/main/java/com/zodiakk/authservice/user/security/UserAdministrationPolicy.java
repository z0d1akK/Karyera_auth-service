package com.zodiakk.authservice.user.security;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.exception.ErrorMessages;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class UserAdministrationPolicy {

    public void checkCreatePermission(Role operatorRole, Role targetRole) {
        if (operatorRole == Role.ADMIN) {
            return;
        }

        if (operatorRole == Role.HR && (targetRole == Role.EMPLOYEE || targetRole == Role.MANAGER)) {
            return;
        }

        throw new AccessDeniedException(ErrorMessages.ACCESS_DENIED);
    }

    public void checkUpdatePermission(Role operatorRole) {
        if (operatorRole != Role.ADMIN) {
            throw new AccessDeniedException(ErrorMessages.ACCESS_DENIED);
        }
    }
}
