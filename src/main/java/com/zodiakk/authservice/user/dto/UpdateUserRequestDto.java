package com.zodiakk.authservice.user.dto;

import com.zodiakk.authservice.auth.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequestDto {

    @NotNull
    private Role role;

    @NotNull
    private Boolean active;
}
