package com.zodiakk.authservice.user.dto;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequestDto {

    @NotNull(message = ValidationMessages.EMPLOYEE_ID_REQUIRED)
    private UUID employeeId;

    @NotNull(message = ValidationMessages.ROLE_REQUIRED)
    private Role role;

    @NotBlank(message = ValidationMessages.USER_PASSWORD_REQUIRED)
    @Size(min = 8, max = 255, message = ValidationMessages.USER_PASSWORD_SIZE)
    private String initialPassword;
}
