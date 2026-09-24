package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mapea entidades a los DTOs de lectura del mapa/detalle de eventos.
 * liveNow e instagramUrl se resuelven aca mismo (via expression) porque solo
 * dependen del propio grafo de Events, no de queries agregadas externas.
 * nullValuePropertyMappingStrategy = IGNORE a nivel de @Mapper: en updateEntityFromDto,
 * cualquier campo del DTO que venga null deja el valor existente de la entidad intacto (no lo pisa).
 * Esto no afecta a toEventCardDto/toEventDetailDto/toEntity porque esos no tienen @MappingTarget (arrancan de un objeto nuevo).
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {EventTimingUtils.class, OffsetDateTime.class})
public interface EventsMapper {

    @Mapping(target = "seriesId", source = "series.id")
    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(event))")
    @Mapping(target = "liveNow",  expression = "java(EventTimingUtils.isLiveNow(event, OffsetDateTime.now()))")
    EventCardDto toEventCardDto(Events event);

    @Mapping(target = "seriesId", source = "series.id")
    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "danceStyles", expression = "java(toStyleNames(event))")
    @Mapping(target = "liveNow", expression = "java(EventTimingUtils.isLiveNow(event, OffsetDateTime.now()))")
    @Mapping(target = "instagramUrl", expression = "java(resolveInstagramUrl(event))")
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

    default String resolveInstagramUrl(Events event) {
        return event.getInstagramUrl() != null
                ? event.getInstagramUrl()
                : event.getOrganizer().getInstagram();
    }
}