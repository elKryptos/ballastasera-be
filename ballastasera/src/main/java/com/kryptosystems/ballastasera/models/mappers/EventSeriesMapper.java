package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.EventSeriesCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesSummaryDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesUpdateDto;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EventSeriesMapper {

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(eventSeries))")
    EventSeriesSummaryDto toEventSeriesSummaryDto(EventSeries eventSeries);

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(eventSeries))")
    @Mapping(target = "instagramUrl", ignore = true)
    EventSeriesDetailDto toEventSeriesDetailDto(EventSeries eventSeries);


    /** Solo copia campos escalares. organizer/venue/city/danceStyles se
     * resuelven aparte en el service (necesitan ir a buscar entidades por
     * id y aplicar reglas de negocio). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "danceStyles", ignore = true)
    @Mapping(target = "events", ignore = true)
    EventSeries toEventSeriesEntity(EventSeriesCreateDto eventSeriesCreateDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "danceStyles", ignore = true)
    @Mapping(target = "events", ignore = true)
    @Mapping(target = "free", source = "isFree")
    void updateEventSeriesEntityFromDto(EventSeriesUpdateDto eventSeriesUpdateDto, @MappingTarget EventSeries eventSeries);

    default List<String> toStyleNames(EventSeries eventSeries) {
        return eventSeries.getDanceStyles().stream()
                .map(DanceStyles::getName)
                .sorted()
                .toList();
    }
}
