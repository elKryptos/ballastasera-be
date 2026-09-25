package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import com.kryptosystems.ballastasera.models.entities.Events;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EventSeriesService {
    List<EventSeries> findAll();
    EventSeries findById(UUID id);
    List<EventSeries> findByOrganizerId(UUID organizerId);
    List<EventSeries> findByVenueId(UUID venueId);
    List<EventSeries> findByCityId(Long cityId);
    EventSeriesDetailDto getEventSeriesDetail(UUID id);
    EventSeriesDetailDto toEventSeriesDetailDto(EventSeries series);
    EventSeries create(UUID requesterId, EventSeriesCreateDto eventCreateDto);
    EventSeries createAsAdmin(EventSeriesCreateDto dto);
    EventSeries update(UUID id, UUID requesterId, EventSeriesUpdateDto eventUpdateDto);
    void delete(UUID id, UUID requesterId);
    EventSeries removeVenue(UUID seriesId, UUID requesterId);

    /** Admin sube o reemplaza el flyer de la serie, sin chequeo de ownership.
     * Se convierte a webp y se publica una sola vez: las ocurrencias que se
     * generen despues heredan flyerUrl de la serie en vez de subir su propia copia. */
    EventSeries updateFlyerAsAdmin(UUID seriesId, MultipartFile file);

    /** Genera los Events concretos de la serie para los días de recurrencia
     * dentro de [from, until]. Idempotente respecto a lo ya generado: si
     * generatedUntil cae dentro del rango pedido, arranca desde el día
     * siguiente en vez de duplicar ocurrencias. */
    List<Events> generateOccurences(UUID seriesId, UUID requesterId, LocalDate startDate, LocalDate endDate);

    /** Igual que generateOccurrences pero sin chequeo de ownership. */
    List<Events> generateOccurencesAsAdmin(UUID seriesId, LocalDate startDate, LocalDate endDate);
}
