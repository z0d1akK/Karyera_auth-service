package com.zodiakk.authservice.registration.dto.response;

import com.zodiakk.authservice.registration.entity.RegistrationRequestStatus;
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
public class RegistrationRequestResponseDto {

    @Schema(description = "Registration request identifier")
    private UUID id;

    @Schema(description = "Employee identifier")
    private UUID employeeId;

    @Schema(description = "User email")
    private String email;

    @Schema(description = "Registration request status")
    private RegistrationRequestStatus status;

    @Schema(description = "Identifier of the employee who processed the request")
    private UUID processedBy;

    @Schema(description = "Request creation time")
    private OffsetDateTime createdAt;

    @Schema(description = "Last modification time")
    private OffsetDateTime updatedAt;
}