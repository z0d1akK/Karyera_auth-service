package com.zodiakk.authservice.user.controller;

import com.zodiakk.authservice.common.dto.response.ApiErrorResponse;
import com.zodiakk.authservice.common.dto.response.ValidationErrorResponse;
import com.zodiakk.authservice.user.dto.CreateUserRequestDto;
import com.zodiakk.authservice.user.dto.ResetUserPasswordRequestDto;
import com.zodiakk.authservice.user.dto.UpdateUserRequestDto;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import com.zodiakk.authservice.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User account administration")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Create user account", description = "Creates an account for an existing employee. "
                    + "HR may create EMPLOYEE and MANAGER accounts; "
                    + "ADMIN may create accounts with any role."
    )
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Invalid request or employee state",
            content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))
    )
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @ApiResponse(responseCode = "409", description = "Account already exists")
    @ApiResponse(responseCode = "503", description = "Employee Service unavailable")
    public ResponseEntity<UserResponseDto> create(@Valid @RequestBody CreateUserRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Get users", description = "Returns a paginated list of user accounts.")
    @ApiResponse(responseCode = "200", description = "Users retrieved")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    public ResponseEntity<Page<UserResponseDto>> getAll(Pageable pageable) {
        return ResponseEntity.ok(userService.getAll(pageable));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Get user by employee ID")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponseDto> getByEmployeeId(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(userService.getByEmployeeId(employeeId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user account", description = "Updates role and activity. "
                    + "The last active administrator cannot be deactivated or demoted."
    )
    @ApiResponse(responseCode = "200", description = "Account updated")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Administrator role required")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "Last active administrator protection",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
    )
    public ResponseEntity<UserResponseDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequestDto request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate user account")
    @ApiResponse(responseCode = "204", description = "Account activated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Administrator role required")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate user account")
    @ApiResponse(responseCode = "204", description = "Account deactivated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Administrator role required")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "Last active administrator")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset user password", description = "Assigns a temporary password, revokes refresh tokens "
                    + "and requires a password change before the next login."
    )
    @ApiResponse(responseCode = "204", description = "Password reset")
    @ApiResponse(responseCode = "400", description = "Invalid password")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Administrator role required")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetUserPasswordRequestDto request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start logical user deletion")
    @ApiResponse(responseCode = "202", description = "Account deletion has been accepted")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.accepted().build();
    }
}