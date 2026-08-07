package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.entities.Events;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Ids de eventos publicados que siguen "vivos" para el mapa: en curso o
     * todavia por empezar (nunca pasados), dentro del bounding box visible.
     * Un evento sin end_at se asume que dura 4h desde start_at.
     */
    @Query(value = """
            SELECT e.id FROM events e
            WHERE e.status = 'PUBLISHED'
              AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
              AND e.latitude  BETWEEN :minLat AND :maxLat
              AND e.longitude BETWEEN :minLng AND :maxLng
              AND (:cityId IS NULL OR e.city_id = :cityId)
            ORDER BY e.start_at ASC
            """, nativeQuery = true)
    List<UUID> findActiveOrUpcomingIdsInBounds(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("cityId") Long cityId);

    /**
     * Carga completa (organizer, venue, dance styles) de un lote de eventos.
     * Se usa junto con findActiveOrUpcomingIdsInBounds para evitar N+1 al
     * armar las cards del mapa; el orden original se reaplica en el service.
     */
    @Query("""
            SELECT DISTINCT e FROM Events e
            JOIN FETCH e.organizer o
            LEFT JOIN FETCH e.venue v
            LEFT JOIN FETCH e.danceStyles ds
            WHERE e.id IN :ids
            """)
    List<Events> findAllWithDetailsByIdIn(@Param("ids") List<UUID> ids);

    @Query("""
            SELECT e FROM Events e
            JOIN FETCH e.organizer o
            LEFT JOIN FETCH e.venue v
            LEFT JOIN FETCH e.city c
            LEFT JOIN FETCH e.danceStyles ds
            WHERE e.id = :id
            """)
    Optional<Events> findByIdWithDetails(@Param("id") UUID id);
}
