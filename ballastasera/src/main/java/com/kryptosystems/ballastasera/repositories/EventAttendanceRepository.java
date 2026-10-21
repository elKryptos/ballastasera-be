package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, UserEventId> {
    List<EventAttendance> findByUserId(UUID userId);
    List<EventAttendance> findByEventId(UUID eventId);

    @Query("SELECT a.id.eventId FROM EventAttendance a WHERE a.user.id = :userId")
    List<UUID> findEventsIdsByUserId( @Param("userId") UUID userId);

    /**
     * Solo quienes van Y activaron show_profile_public. El conteo total de
     * "van" (arriba) es independiente y siempre incluye a todos.
     */
    @Query("""
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.user u
            WHERE ea.event.id = :eventId AND u.showProfilePublic = true
            ORDER BY ea.createdAt ASC
            """)
    Page<EventAttendance> findByEventIdAndStatusAndUserShowProfilePublicTrue(
            @Param("eventId") UUID eventId, Pageable pageable);

    /** Ids de los eventos a los que el usuario marco "voy", para /me/attendance:
     * solo publicados y en curso o futuros, los mas cercanos primero.
     * Mismo keyset (start_at, id) y mismo patron ids + Slice que FavoritesRepository. */
    @Query(value = """
            SELECT e.id FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
            AND (e.start_at, e.id) > (:cursorStartAt, :cursorId)
            ORDER BY e.start_at ASC, e.id ASC
            """, nativeQuery = true)
    Slice<UUID> findUpcomingEventsIdsByUserId(@Param("userId") UUID userId,
                                              @Param("cursorStartAt") OffsetDateTime cursorStartAt,
                                              @Param("cursorId") UUID cursorId,
                                              Pageable pageable);

    /** Total para el contador de /me/attendance, con el mismo filtro que la lista. */
    @Query(value = """
            SELECT COUNT(*) FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
            """, nativeQuery = true)
    long countUpcomingByUserId(@Param("userId") UUID userId);

    /** Ids de eventos con "voy" ya terminados, para /me/attendance?past=true:
     * del mas reciente al mas antiguo. Keyset invertido igual que en FavoritesRepository. */
    @Query(value = """
            SELECT e.id FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            AND (e.start_at , e.id) < (:cursorStartAt, :cursorId)
            ORDER BY e.start_at DESC, e.id DESC
            """, nativeQuery = true)
    Slice<UUID> findPastEventsIdsByUserId(@Param("userId") UUID userId,
                                          @Param("cursorStartAt") OffsetDateTime cursorStartAt,
                                          @Param("cursorId") UUID cursorId,
                                          Pageable pageable);

    /** Total para el contador de serate passate en /me/attendance. */
    @Query(value = """
            SELECT COUNT(*) FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            """, nativeQuery = true)
    long countPastByUserId(@Param("userId") UUID userId);

    /** Primeros asistentes publicos con foto de cada evento, para las caritas
     * del mapa y la lista. Mismo filtro de privacidad que findByEventIdAndStatus... */
    @Query(value = """
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.user u
            WHERE ea.id.eventId IN :eventIds
            AND u.showProfilePublic = true AND u.avatarUrl IS NOT NULL
            AND u.isDeleted = false AND u.isBanned = false
            AND (SELECT COUNT(ea2) FROM EventAttendance ea2 JOIN ea2.user u2
               WHERE ea2.id.eventId = ea.id.eventId
               AND u2.showProfilePublic = true AND u2.avatarUrl IS NOT NULL
               AND u2.isDeleted = false AND u2.isBanned = false
               AND ea2.createdAt < ea.createdAt) < :perEvent
          ORDER BY ea.createdAt ASC
          """)
    List<EventAttendance> findAvatarPreviewsByEventIdIn(@Param("eventIds") Collection<UUID> eventIds,
                                                        @Param("perEvent") long perEvent);
}
