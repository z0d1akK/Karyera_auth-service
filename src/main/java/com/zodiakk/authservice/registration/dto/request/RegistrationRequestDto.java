package com.zodiakk.authservice.registration.dto.request;

import com.zodiakk.authservice.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationRequestDto {

    @Schema(description = "Employee identifier", example = "e73dcc73-e1db-4c3a-9246-0e1c2de79074")
    @NotNull(message = ValidationMessages.USER_ID_REQUIRED)
    private UUID employeeId;

    @Schema(description = "User email", example = "user@gmail.com")
    @NotBlank(message = ValidationMessages.USER_EMAIL_REQUIRED)
    @Email(message = ValidationMessages.USER_EMAIL_VALID)
    @Size(max = 255, message = ValidationMessages.USER_EMAIL_SIZE)
    private String email;

    @Schema(description = "User password", example = "qwerty123A")
    @NotBlank(message = ValidationMessages.USER_PASSWORD_REQUIRED)
    @Size(min = 8, max = 255, message = ValidationMessages.USER_PASSWORD_SIZE)
    private String password;
}