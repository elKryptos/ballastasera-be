package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.EventSeriesSummaryDto;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventSeriesMapper {

    EventSeriesSummaryDto toEventSeriesSummaryDto(EventSeries eventSeries);
}
