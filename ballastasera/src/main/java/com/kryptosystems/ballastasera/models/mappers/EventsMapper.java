package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.Events;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapea entidades a los DTOs de lectura del mapa/detalle de eventos.
 * Los campos que dependen de "ahora" (liveNow) o de queries agregadas
 * (goingCount, interestedCount, fallback de instagramUrl) NO se mapean
 * aqui: se completan en EventsServiceImpl porque necesitan datos que no
 * vienen de la propia entidad Events.
 * nullValuePropertyMappingStrategy = IGNORE a nivel de @Mapper: en updateEntityFromDto,
 * cualquier campo del DTO que venga null deja el valor existente de la entidad intacto (no lo pisa).
 * Esto no afecta a toEventCardDto/toEventDetailDto/toEntity porque esos no tienen @MappingTarget (arrancan de un objeto nuevo).
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EventsMapper {

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(event))")
    @Mapping(target = "liveNow", ignore = true)
    @Mapping(target = "goingCount", ignore = true)
    EventCardDto toEventCardDto(Events event);

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(event))")
    @Mapping(target = "liveNow", ignore = true)
    @Mapping(target = "goingCount", ignore = true)
    @Mapping(target = "interestedCount", ignore = true)
    @Mapping(target = "instagramUrl", ignore = true)
    EventDetailDto toEventDetailDto(Events event);

    /** Solo copia campos escalares. organizer/venue/city/series/danceStyles,
     * id, slug y status se resuelven aparte en el service (necesitan ir a
     * buscar entidades por id y aplicar reglas de negocio). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "danceStyles", ignore = true)
    @Mapping(target = "favorites", ignore = true)
    @Mapping(target = "eventAttendances", ignore = true)
    Events toEventEntity(EventCreateDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "danceStyles", ignore = true)
    @Mapping(target = "favorites", ignore = true)
    @Mapping(target = "eventAttendances", ignore = true)
    @Mapping(target = "free", source = "isFree")
    void updateEventEntityFromDto(EventUpdateDto dto, @MappingTarget Events event);

    default List<String> toStyleNames(Events event) {
        return event.getDanceStyles().stream()
                .map(DanceStyles::getName)
                .sorted()
                .toList();
    }
}