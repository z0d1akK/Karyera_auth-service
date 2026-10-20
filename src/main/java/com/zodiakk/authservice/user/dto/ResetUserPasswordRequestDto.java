package com.zodiakk.authservice.user.dto;

import com.zodiakk.authservice.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Administrative password reset request")
public class ResetUserPasswordRequestDto {

    @NotBlank(message = ValidationMessages.USER_PASSWORD_REQUIRED)
    @Size(min = 12, max = 128, message = ValidationMessages.TEMPORARY_PASSWORD_SIZE)
    @Schema(
            description = "New temporary password",
            example = "Temporary#2026Password",
            accessMode = Schema.AccessMode.WRITE_ONLY
    )
    private String temporaryPassword;
}