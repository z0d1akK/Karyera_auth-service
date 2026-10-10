package com.zodiakk.authservice.auth.dto.request;

import com.zodiakk.authservice.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
public class RefreshTokenRequestDto {

    @Schema(description = "Refresh token", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = ValidationMessages.REFRESH_TOKEN_REQUIRED)
    private String refreshToken;
}