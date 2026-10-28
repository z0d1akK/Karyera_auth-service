package com.zodiakk.authservice.auth.controller;

import com.zodiakk.authservice.auth.dto.request.ValidateTokenRequestDto;
import com.zodiakk.authservice.auth.dto.response.TokenValidationResponseDto;
import com.zodiakk.authservice.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Internal Authentication", description = "Internal API for trusted services")
@SecurityRequirement(name = "internalApiKey")
public class InternalAuthController {

    private final AuthService authService;

    @PostMapping("/validate")
    @Operation(summary = "Validate access token", description = "Checks token signature, expiration, user activity, "
                    + "current role and password-change state."
    )
    @ApiResponse(responseCode = "200", description = "Validation result returned")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid internal API key")
    @ApiResponse(responseCode = "403", description = "Internal access denied")
    public ResponseEntity<TokenValidationResponseDto> validateToken(@Valid @RequestBody ValidateTokenRequestDto request) {
        return ResponseEntity.ok(authService.validateToken(request));
    }
}