package com.zodiakk.authservice.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponseDto {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "JWT refresh token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;

    @Schema(description = "Token type", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";
}