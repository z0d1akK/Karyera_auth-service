package com.zodiakk.authservice.auth.service;

public interface RefreshTokenService {

    void cleanupExpiredTokens();
}
