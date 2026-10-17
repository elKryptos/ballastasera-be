package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface FavoritesRepository extends JpaRepository<Favorites, UserEventId> {
    List<Favorites> findByUserId(UUID userId);
    List<Favorites> findByEventId(UUID eventId);

    @Query("SELECT f.id.eventId FROM Favorites f WHERE f.id.userId = :userId")
    List<UUID> findEventsIdsByUserId(@Param("userId") UUID userId);

    /** Ids de los favoritos del usuario para /me/favorites: solo publicados y
     * en curso o futuros, ordenados por fecha de inicio (los mas cercanos primero).
     * Keyset sobre (start_at, id): devuelve los que vienen despues del cursor.
     * Solo ids + Slice (size + 1, sin count) y luego findAllWithDetailsByIdIn:
     * paginar con JOIN FETCH de danceStyles haría que Hibernate pagine en memoria. */
    @Query(value = """
            SELECT e.id FROM favorites f JOIN events e ON e.id = f.event_id
            WHERE f.user_id = :userId
              AND e.status = 'PUBLISHED'
              AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
              AND (e.start_at, e.id) > (:cursorStartAt, :cursorId)
            ORDER BY e.start_at ASC, e.id ASC
            """, nativeQuery = true)
    Slice<UUID>  findUpcomingEventsIdsByUserId(@Param("userId") UUID userId,
                                               @Param("cursorStartAt") OffsetDateTime cursorStartAt,
                                               @Param("cursorId") UUID cursorId,
                                               Pageable pageable);

    /** Total para el contador de /me/favorites, con el mismo filtro que la lista. */
    @Query(value = """
            SELECT COUNT(*) FROM favorites f JOIN events e ON e.id = f.event_id
            WHERE f.user_id = :userId
              AND e.status = 'PUBLISHED'
              AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
            """, nativeQuery = true)
    long countUpcomingByUserId(@Param("userId") UUID userId);

    /** Ids de favoritos ya terminados, para /me/favorites?past=true: del más reciente
     * al más antiguo. Keyset invertido: (start_at, id) < cursor con ORDER BY DESC. */
    @Query(value = """
            SELECT e.id FROM favorites f JOIN events e ON e.id = f.event_id
            WHERE f.user_id = :userId
              AND e.status = 'PUBLISHED'
              AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
              AND (e.start_at, e.id) < (:cursorStartAt, :cursorId)
            ORDER BY e.start_at DESC, e.id DESC
            """, nativeQuery = true)
    Slice<UUID> findPastEventsIdsByUserId(@Param("userId") UUID userId,
                                          @Param("cursorStartAt") OffsetDateTime cursorStartAt,
                                          @Param("cursorId") UUID cursorId,
                                          Pageable pageable);

    /** Total para el contador de serate passate en /me/favorites. */
    @Query(value = """
            SELECT COUNT(*) FROM favorites f JOIN events e ON e.id = f.event_id
            WHERE f.user_id = :userId
              AND e.status = 'PUBLISHED'
              AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            """, nativeQuery = true)
    long countPastByUserId(@Param("userId") UUID userId);
}
