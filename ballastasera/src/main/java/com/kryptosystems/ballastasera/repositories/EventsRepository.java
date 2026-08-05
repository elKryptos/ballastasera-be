package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.entities.Events;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventsRepository extends JpaRepository<Events, UUID> {
    Optional<Events> findBySlug(String slug);
    List<Events> findByOrganizerId(UUID organizerId);
    List<Events> findByVenueId(UUID venueId);
    List<Events> findBySeriesId(UUID seriesId);
    List<Events> findByCityIdAndStatusAndStartAtGreaterThanEqualOrderByStartAtAsc(
            Long cityId, EventStatus status, OffsetDateTime from);
}
