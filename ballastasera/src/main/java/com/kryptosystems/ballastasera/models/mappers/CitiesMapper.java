package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.CityDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CitiesMapper {
    CityDto toDto(Cities city);
}
