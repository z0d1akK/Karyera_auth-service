package com.zodiakk.authservice.auth.dto.request;

import com.zodiakk.authservice.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class LoginRequestDto {

    @Schema(description = "User login", example = "test@company.com")
    @NotBlank(message = ValidationMessages.USER_EMAIL_REQUIRED)
    @Size(max = 255, message = ValidationMessages.USER_EMAIL_SIZE)
    private String login;

    @Schema(description = "User password", example = "qwerty123A")
    @NotBlank(message = ValidationMessages.USER_PASSWORD_REQUIRED)
    @Size(max = 255, message = ValidationMessages.USER_PASSWORD_SIZE)
    private String password;
}