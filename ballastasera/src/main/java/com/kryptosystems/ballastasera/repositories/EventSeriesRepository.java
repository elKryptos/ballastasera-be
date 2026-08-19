package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.EventSeries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSeriesRepository extends JpaRepository<EventSeries, UUID> {
    List<EventSeries> findByOrganizerId(UUID organizerId);
    List<EventSeries> findByVenueId(UUID venueId);
    List<EventSeries> findByCityId(Long cityId);

    @Query("""
            SELECT s FROM EventSeries s
            JOIN FETCH s.organizer o
            LEFT JOIN FETCH s.venue v
            JOIN FETCH s.city c
            LEFT JOIN FETCH s.danceStyles ds
            WHERE s.id = :id
            """)
    Optional<EventSeries> findByIdWithDetails(@Param("id") UUID id);
}
