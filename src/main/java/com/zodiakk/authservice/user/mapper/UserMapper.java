package com.zodiakk.authservice.user.mapper;

import com.zodiakk.authservice.auth.entity.AuthCredential;
import com.zodiakk.authservice.config.MapStructConfig;
import com.zodiakk.authservice.user.dto.UserResponseDto;
import com.zodiakk.authservice.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    @Mapping(target = "id",                     source = "user.id")
    @Mapping(target = "employeeId",             source = "user.employeeId")
    @Mapping(target = "email",                  source = "user.email")
    @Mapping(target = "active",                 source = "user.active")
    @Mapping(target = "createdAt",              source = "user.createdAt")
    @Mapping(target = "updatedAt",              source = "user.updatedAt")
    @Mapping(target = "role",                   source = "credential.role")
    @Mapping(target = "passwordChangeRequired", source = "credential.passwordChangeRequired")
    UserResponseDto toResponseDto(User user, AuthCredential credential);
}