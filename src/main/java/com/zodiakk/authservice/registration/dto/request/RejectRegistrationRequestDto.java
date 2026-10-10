package com.zodiakk.authservice.registration.dto.request;

import com.zodiakk.authservice.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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
public class RejectRegistrationRequestDto {

    @Schema(description = "Reason for rejecting registration request", example = "Employee information could not be verified")
    @Size(max = 1000, message = ValidationMessages.REGISTRATION_REJECTION_REASON_SIZE)
    private String reason;
}