package com.zodiakk.authservice.common.dto.response;

import com.zodiakk.authservice.common.dto.field.ValidationErrorField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Validation error response")
public class ValidationErrorResponse {

    @Schema(example = "400")
    private int status;

    @Schema(example = "Bad Request")
    private String error;

    private List<ValidationErrorField> errors;

    @Schema(example = "2026-05-16T15:30:00")
    private LocalDateTime timestamp;
}