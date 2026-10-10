package com.zodiakk.authservice.registration.mapper;

import com.zodiakk.authservice.config.MapStructConfig;
import com.zodiakk.authservice.registration.dto.response.RegistrationRequestResponseDto;
import com.zodiakk.authservice.registration.entity.RegistrationRequest;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface RegistrationRequestMapper {

    RegistrationRequestResponseDto toResponseDto(RegistrationRequest registrationRequest);
}