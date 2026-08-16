package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.DanceStyleDto;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DanceStylesMapper {
    DanceStyleDto toDto(DanceStyles danceStyle);
}
