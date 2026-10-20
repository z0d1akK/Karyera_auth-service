package com.zodiakk.authservice.auth.security.utils;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.security.jwt.principal.AuthenticatedUser;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.UUID;

@UtilityClass
public class SecurityContextUtils {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user is required");
        }

        return user;
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public Role getCurrentUserRole() {
        return getCurrentUser().getRole();
    }
}
