package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, UserEventId> {
    List<EventAttendance> findByUserId(UUID userId);
    List<EventAttendance> findByEventId(UUID eventId);
    long countByEventIdAndStatus(UUID eventId, AttendanceStatus status);

    /**
     * Conteo de "van" por evento, en lote, para las cards del mapa.
     * Object[]: [0] = eventId (UUID), [1] = total (Long).
     * El status va como parametro bindeado (no como literal en el JPQL) para
     * que Hibernate lo resuelva siempre como el enum de Java, nunca como un
     * cast de tipo Postgres.
     */
    @Query("""
            SELECT ea.event.id, COUNT(ea) FROM EventAttendance ea
            WHERE ea.event.id IN :eventIds AND ea.status = :status
            GROUP BY ea.event.id
            """)
    List<Object[]> countByEventIdInAndStatus(@Param("eventIds") List<UUID> eventIds, @Param("status") AttendanceStatus status);

    /**
     * Solo quienes van Y activaron show_profile_public. El conteo total de
     * "van" (arriba) es independiente y siempre incluye a todos.
     */
    @Query("""
            SELECT ea FROM EventAttendance ea
            JOIN FETCH ea.user u
            WHERE ea.event.id = :eventId
              AND ea.status = :status
              AND u.showProfilePublic = true
            ORDER BY ea.createdAt ASC
            """)
    Page<EventAttendance> findByEventIdAndStatusAndUserShowProfilePublicTrue(
            @Param("eventId") UUID eventId, @Param("status") AttendanceStatus status, Pageable pageable);
}
