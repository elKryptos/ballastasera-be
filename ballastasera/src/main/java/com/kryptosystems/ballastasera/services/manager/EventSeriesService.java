package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.EventSeries;

import java.util.List;
import java.util.UUID;

public interface EventSeriesService {
    List<EventSeries> findAll();
    EventSeries findById(UUID id);
    List<EventSeries> findByOrganizerId(UUID organizerId);
    EventSeries save(EventSeries series);
    void deleteById(UUID id);
}
