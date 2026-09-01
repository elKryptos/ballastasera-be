package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Events;
import org.springframework.web.multipart.MultipartFile;

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
     * no le pertenece a requesterId o si aun no esta verificado. Nace en PENDING. */
    Events create(UUID requesterId, EventCreateDto dto);

    /** Admin crea el evento para cualquier organizer (reclamado o no), sin chequeo
     * de ownership ni de verificación. Nace en PENDING igual que create(). */
    Events createAsAdmin(EventCreateDto dto);

    /** Edita un evento propio. No permite cambiar de organizer ni de status. */
    Events update(UUID id, UUID requesterId, EventUpdateDto dto);

    /** Publicar / despublicar / cancelar un evento propio. */
    Events updateStatus(UUID requesterId, UUID id, EventStatus status);

    Events updateFlyer(UUID id, UUID requesterId, MultipartFile file);

    /** Admin sube/reemplaza el flyer de cualquier evento, sin chequeo de ownership. */
    Events updateFlyerAsAdmin(UUID id, MultipartFile file);

    /** Elimina el flyer de un evento propio (raw + final) y vuelve el status a NONE. */
    Events deleteFlyer(UUID id, UUID requesterId);

    /** Admin elimina el flyer de cualquier evento, sin chequeo de ownership. */
    Events deleteFlyerAsAdmin(UUID id);

    /** Borra un evento propio (ownership check incluido). */
    void delete(UUID id, UUID requesterId);

    /** Remueve el venue del evento si presente. Debe ser el organizador y owner del evento. */
    Events removeVenue(UUID eventId, UUID requesterId);

    List<Events> findPublishedByOrganizerId(UUID organizerId);
}
