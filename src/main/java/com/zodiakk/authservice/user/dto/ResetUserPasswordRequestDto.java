package com.zodiakk.authservice.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Administrative password reset request")
public class ResetUserPasswordRequestDto {

    @NotBlank
    @Size(min = 12, max = 128)
    @Schema(
            description = "New temporary password",
            example = "Temporary#2026Password",
            accessMode = Schema.AccessMode.WRITE_ONLY
    )
    private String temporaryPassword;
}