package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.ReportStatus;
import com.kryptosystems.ballastasera.models.entities.UserReports;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReportsRepository extends JpaRepository<UserReports, UUID> {

    /** ¿reporter ya tiene una denuncia abierta contra reported? Una sola OPEN por pareja. */
    boolean existsByReporterIdAndReportedIdAndStatus(UUID reporterId, UUID reportedId, ReportStatus status);

    /** Cuantas denuncias abiertas tiene reported: el contador de cada fila de la cola del admin. */
    long countByReportedIdAndStatus(UUID reportedId, ReportStatus status);

    /** Al banear: las abiertas contra reported, para cerrarlas todas como BANNED. */
    List<UserReports> findByReportedIdAndStatus(UUID reportedId, ReportStatus status);

    /** Cola del admin. El orden llega en el Pageable. countQuery aparte porque el count no admite JOIN FETCH. */
    @Query(value = """
        SELECT r FROM UserReports r
        JOIN FETCH r.reporter rp
        JOIN FETCH r.reported rd
        LEFT JOIN FETCH r.reviewedBy rb
        WHERE r.status = :status
        """, countQuery = "SELECT COUNT(r) FROM UserReports r WHERE r.status = :status")
    Page<UserReports> findByStatusWithDetails(ReportStatus status, Pageable pageable);

    @Query("""
        SELECT r FROM UserReports r
        JOIN FETCH r.reporter rp
        JOIN FETCH r.reported rd
        LEFT JOIN FETCH r.reviewedBy rb
        WHERE r.id = :id
        """)
    Optional<UserReports> findByIdWithDetails(UUID id);
}
