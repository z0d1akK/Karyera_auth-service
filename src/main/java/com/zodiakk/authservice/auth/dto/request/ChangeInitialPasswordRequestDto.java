package com.zodiakk.authservice.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeInitialPasswordRequestDto {

    @NotBlank
    private String login;

    @NotBlank
    private String initialPassword;

    @NotBlank
    @Size(min = 12, max = 255)
    private String newPassword;
}
