package com.zodiakk.authservice.auth.service;

import com.zodiakk.authservice.auth.dto.request.ChangeInitialPasswordRequestDto;
import com.zodiakk.authservice.auth.dto.request.LoginRequestDto;
import com.zodiakk.authservice.auth.dto.request.RefreshTokenRequestDto;
import com.zodiakk.authservice.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.authservice.auth.dto.response.TokenResponseDto;
import com.zodiakk.authservice.auth.dto.response.TokenValidationResponseDto;

public interface AuthService {

    /**
     * Creates new access and refresh tokens based on provided login credentials.
     *
     * @param request request object containing login credentials
     * @return response containing access and refresh tokens
     */
    TokenResponseDto login(LoginRequestDto request);

    /**
     * Refreshes expired access token using valid refresh token.
     *
     * @param request request object containing refresh token
     * @return response containing new access and refresh tokens
     */
    TokenResponseDto refreshToken(RefreshTokenRequestDto request);

    /**
     * Validates provided access token and returns its metadata.
     *
     * @param request request object containing token to validate
     * @return response containing token validation result and metadata
     */
    TokenValidationResponseDto validateToken(ValidateTokenRequestDto request);

    /**
     * Logout current user.
     *
     * @param request request object containing refresh token of user
     */
    void logout(RefreshTokenRequestDto request);

    /**
     * Changes initial password for current user.
     *
     * @param request request object to change password
     */
    void changeInitialPassword(ChangeInitialPasswordRequestDto request);
}
