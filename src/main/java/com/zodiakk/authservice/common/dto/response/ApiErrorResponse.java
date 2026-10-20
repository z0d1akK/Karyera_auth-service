package com.zodiakk.authservice.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(example = "true")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean valid;

    @Schema(example = "404")
    private int status;

    @Schema(example = "PASSWORD_CHANGE_REQUIRED")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;

    @Schema(example = "Not Found")
    private String error;

    @Schema(example = "User with id ... not found")
    private String message;

    @Schema(example = "2026-05-16T15:30:00")
    private LocalDateTime timestamp;
}