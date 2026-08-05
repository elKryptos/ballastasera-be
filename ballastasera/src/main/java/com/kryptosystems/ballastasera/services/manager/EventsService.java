package com.kryptosystems.ballastasera.services.manager;

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
}
