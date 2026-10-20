package com.zodiakk.authservice.user.dto;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequestDto {

    @NotNull(message = ValidationMessages.ROLE_REQUIRED)
    private Role role;

    @NotNull(message = ValidationMessages.ACTIVE_REQUIRED)
    private Boolean active;
}
