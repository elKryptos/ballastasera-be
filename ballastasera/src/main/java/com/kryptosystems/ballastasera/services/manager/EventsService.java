package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Events;

import java.util.List;
import java.util.UUID;

public interface EventsService {
    List<Events> findAll();
    Events findById(UUID id);
    Events findBySlug(String slug);
    List<Events> findByOrganizerId(UUID organizerId);
    List<Events> findByVenueId(UUID venueId);
    List<Events> findBySeriesId(UUID seriesId);
    List<Events> findUpcomingPublishedByCity(Long cityId);
    Events save(Events event);
    void deleteById(UUID id);

    /** Eventos en vivo o por empezar dentro del bounding box del mapa. */
    List<EventCardDto> findMapEvents(double minLat, double maxLat, double minLng, double maxLng, Long cityId);

    EventDetailDto getEventDetail(UUID id);

    /** Igual que getEventDetail pero a partir de un Events ya cargado en memoria,
     * sin volver a consultar la DB. Pensado para create/update/updateStatus. */
    EventDetailDto toEventDetailDto(Events event);

    /** Crea el evento a nombre del organizerId del dto; falla si ese organizer
     * no le pertenece a requesterId o si aun no esta verificado. Nace en DRAFT. */
    Events create(UUID requesterId, EventCreateDto dto);

    /** Edita un evento propio. No permite cambiar de organizer ni de status. */
    Events update(UUID id, UUID requesterId, EventUpdateDto dto);

    /** Publicar / despublicar / cancelar un evento propio. */
    Events updateStatus(UUID requesterId, UUID id, EventStatus status);

    /** Borra un evento propio (ownership check incluido). */
    void delete(UUID id, UUID requesterId);
}
