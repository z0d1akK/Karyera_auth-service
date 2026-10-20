package com.zodiakk.authservice.user.dto;

import com.zodiakk.authservice.auth.entity.Role;
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

    @NotNull
    private UUID employeeId;

    @NotNull
    private Role role;

    @NotBlank
    @Size(min = 8, max = 255)
    private String initialPassword;
}
