package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.Events;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapea entidades a los DTOs de lectura del mapa/detalle de eventos.
 * Los campos que dependen de "ahora" (liveNow) o de queries agregadas
 * (goingCount, interestedCount, fallback de instagramUrl) NO se mapean
 * aqui: se completan en EventsServiceImpl porque necesitan datos que no
 * vienen de la propia entidad Events.
 */
@Mapper(componentModel = "spring")
public interface EventsMapper {

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(event))")
    @Mapping(target = "liveNow", ignore = true)
    @Mapping(target = "goingCount", ignore = true)
    EventCardDto toCardDto(Events event);

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(event))")
    @Mapping(target = "liveNow", ignore = true)
    @Mapping(target = "goingCount", ignore = true)
    @Mapping(target = "interestedCount", ignore = true)
    @Mapping(target = "instagramUrl", ignore = true)
    EventDetailDto toDetailDto(Events event);

    default List<String> toStyleNames(Events event) {
        return event.getDanceStyles().stream()
                .map(DanceStyles::getName)
                .sorted()
                .toList();
    }
}