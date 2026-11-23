package com.zodiakk.authservice.auth.security.jwt;

@FunctionalInterface
public interface AccessTokenGuard {

    boolean allows(JwtTokenClaims claims);
}
