package com.zodiakk.authservice.auth.mapper;

import com.zodiakk.authservice.auth.dto.response.AuthCredentialResponseDto;
import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface AuthCredentialMapper {

    AuthCredentialResponseDto toResponseDto(AuthCredential credential);
}