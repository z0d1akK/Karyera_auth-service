package com.zodiakk.authservice.user.service;

import com.zodiakk.authservice.user.dto.CreateUserRequestDto;
import com.zodiakk.authservice.user.dto.ResetUserPasswordRequestDto;
import com.zodiakk.authservice.user.dto.UpdateUserRequestDto;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    /**
     * Creates a new user.
     *
     * @param request request object containing user creation data
     * @return created user response
     */
    UserResponseDto create(CreateUserRequestDto request);

    /**
     * Returns full user information by identifier.
     *
     * @param id user identifier
     * @return full user response
     */
    UserResponseDto getById(UUID id);

    /**
     * Returns full user information by employee identifier.
     *
     * @param employeeId employee identifier
     * @return full user response
     */
    UserResponseDto getByEmployeeId(UUID employeeId);

    /**
     * Returns paginated list of users in full representation.
     * <p>
     * Supports filtering and pagination.
     *
     * @param pageable pagination information
     * @return page of full user responses
     */
    Page<UserResponseDto> getAll(Pageable pageable);

    /**
     * Updates existing user data.
     *
     * @param id user identifier
     * @param request request object containing updated user data
     * @return updated user response
     */
    UserResponseDto update(UUID id, UpdateUserRequestDto request);

    /**
     *
     * @param id user identifier
     * @param request request object containing password to reset
     */
    void resetPassword(UUID id, ResetUserPasswordRequestDto request);

    /**
     * Activates user account.
     *
     * @param id user identifier
     */
    void activate(UUID id);

    /**
     * Deactivates user account.
     *
     * @param id user identifier
     */
    void deactivate(UUID id);

    /**
     * Deletes user account.
     *
     * @param id user identifier
     */
    void delete(UUID id);
}
