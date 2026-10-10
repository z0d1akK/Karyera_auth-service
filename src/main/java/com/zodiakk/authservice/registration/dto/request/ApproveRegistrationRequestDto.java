package com.zodiakk.authservice.registration.dto.request;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class ApproveRegistrationRequestDto {

    @Schema(description = "Role assigned to the new user", example = "EMPLOYEE")
    @NotNull(message = ValidationMessages.USER_ROLE_REQUIRED)
    private Role role;
}