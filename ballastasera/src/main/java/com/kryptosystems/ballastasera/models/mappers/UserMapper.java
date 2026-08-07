package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", source = "id")
    UserDto toDto(Users user);
}