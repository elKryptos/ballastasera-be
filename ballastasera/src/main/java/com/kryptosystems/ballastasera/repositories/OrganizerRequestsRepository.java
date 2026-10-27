package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizerRequestsRepository extends JpaRepository<OrganizerRequests, UUID> {
    /** ¿El usuario tiene una solicitud pendiente? */
    boolean existsByUserIdAndStatus(UUID userId, OrganizerRequestStatus status);
    /** Cuántos REJECTED tiene el usuario. */
    long countByUserIdAndStatus(UUID userId, OrganizerRequestStatus status);
    /** Al aprobar una CLAIM, encuentra las otras CLAIM PENDING del mismo organizador para rechazarlas. También lo usa deleteAsAdmin para
     cerrarlas antes de borrar. */
    List<OrganizerRequests> findByOrganizerIdAndTypeAndStatus(UUID organizerId, OrganizerRequestType type, OrganizerRequestStatus status);

    /** La última solicitud del usuario: GET /me y el logo a copiar al reenviar. */
    @Query("""
        SELECT r FROM OrganizerRequests r
        LEFT JOIN FETCH r.organizer o
        LEFT JOIN FETCH r.venue v
        LEFT JOIN FETCH r.venueCity c
        WHERE r.user.id = :userId
        ORDER BY r.submittedAt DESC
        LIMIT 1
        """)
    Optional<OrganizerRequests> findLatestByUserIdWithDetails(UUID userId);

    /** Cola del admin. El orden llega en el Pageable: lo decide el servicio según el estado.
     * countQuery aparte porque el count no admite JOIN FETCH. */
    @Query(value = """
        SELECT r FROM OrganizerRequests r JOIN FETCH r.user u
        LEFT JOIN FETCH r.organizer o
        LEFT JOIN FETCH r.reviewedBy rb
        LEFT JOIN FETCH r.venue v
        LEFT JOIN FETCH r.venueCity c
        WHERE r.status = :status
        """, countQuery = "SELECT COUNT(r) FROM OrganizerRequests r WHERE r.status = :status")
    Page<OrganizerRequests> findByStatusWithDetails(OrganizerRequestStatus status,
                                                    Pageable pageable);

    @Query("""
        SELECT r FROM OrganizerRequests r JOIN FETCH r.user u
        LEFT JOIN FETCH r.organizer o
        LEFT JOIN FETCH r.reviewedBy rb
        LEFT JOIN FETCH r.venue v
        LEFT JOIN FETCH r.venueCity c
        WHERE r.id = :id
        """)
    Optional<OrganizerRequests> findByIdWithDetails(UUID id);
}
