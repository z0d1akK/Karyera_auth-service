package com.zodiakk.authservice.common.dto.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Validation field error")
public class ValidationErrorField {

    @Schema(example = "email")
    private String field;

    @Schema(example = "Email should be valid")
    private String message;
}