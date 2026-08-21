package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.entities.EventSeries;

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
}
