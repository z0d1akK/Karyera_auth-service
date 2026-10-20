package com.zodiakk.authservice.auth.security.jwt.principal;

import com.zodiakk.authservice.auth.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuthenticatedUser {

    private UUID userId;

    private String email;

    private Role role;
}