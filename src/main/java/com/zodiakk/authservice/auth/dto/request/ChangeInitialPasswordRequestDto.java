package com.zodiakk.authservice.auth.dto.request;

import com.zodiakk.authservice.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeInitialPasswordRequestDto {

    @NotBlank(message = ValidationMessages.USER_EMAIL_REQUIRED)
    private String login;

    @NotBlank(message = ValidationMessages.USER_PASSWORD_REQUIRED)
    private String initialPassword;

    @NotBlank(message = ValidationMessages.USER_PASSWORD_REQUIRED)
    @Size(min = 12, max = 255, message = ValidationMessages.NEW_PASSWORD_SIZE)
    private String newPassword;
}