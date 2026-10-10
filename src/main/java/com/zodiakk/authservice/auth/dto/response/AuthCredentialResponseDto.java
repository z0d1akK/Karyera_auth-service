package com.zodiakk.authservice.auth.dto.response;

import com.zodiakk.authservice.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class AuthCredentialResponseDto {

    @Schema(description = "Credential identifier", example = "e73dcc73-e1db-4c3a-9246-0e1c2de79074")
    private UUID id;

    @Schema(description = "User identifier", example = "e73dcc73-e1db-4c3a-9246-0e1c2de79074")
    private UUID userId;

    @Schema(description = "User login", example = "user@gmail.com")
    private String login;

    @Schema(description = "User role", example = "EMPLOYEE")
    private Role role;
}