package com.zodiakk.authservice.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    @Schema(description = "User identifier")
    private UUID id;

    @Schema(description = "Employee identifier")
    private UUID employeeId;

    @Schema(description = "User email")
    private String email;

    @Schema(description = "Account status")
    private Boolean active;

    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Last modification timestamp")
    private OffsetDateTime updatedAt;
}