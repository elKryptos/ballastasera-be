package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.EventSeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventSeriesRepository extends JpaRepository<EventSeries, UUID> {
    List<EventSeries> findByOrganizerId(UUID organizerId);
}
