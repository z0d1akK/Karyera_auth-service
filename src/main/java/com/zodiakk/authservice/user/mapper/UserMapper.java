package com.zodiakk.authservice.user.mapper;

import com.zodiakk.authservice.config.MapStructConfig;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import com.zodiakk.authservice.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    UserResponseDto toResponseDto(User user);
}