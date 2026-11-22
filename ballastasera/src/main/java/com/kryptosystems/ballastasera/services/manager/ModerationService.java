package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.ReportStatus;
import com.kryptosystems.ballastasera.models.dtos.UserReportCreateDto;
import com.kryptosystems.ballastasera.models.entities.UserReports;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Denuncias y ban. Van juntos porque banear desde una denuncia cierra todas las abiertas contra esa persona,
 * y un ban directo tambien: separados, los dos servicios se llamarian el uno al otro.
 * Nada se oculta solo: una denuncia no cambia nada hasta que el admin la revisa.
 */
public interface ModerationService {

    /** Idempotente: si reporter ya tiene una abierta contra reported, no crea otra.
     * 400 si es uno mismo; 404 si no existe o esta borrado. Se puede denunciar a un perfil privado o bloqueado. */
    void report(UUID reporterId, UUID reportedId, UserReportCreateDto body);

    /** Cola del admin: OPEN las mas viejas primero (se atienden por orden), las revisadas las mas recientes. */
    Page<UserReports> findReportsByStatus(ReportStatus status, Pageable pageable);

    UserReports findReportById(UUID reportId);

    long countOpenReportsAgainst(UUID reportedId);

    /** DISMISSED cierra solo esta; BANNED banea al denunciado y cierra todas sus abiertas.
     * 409 REPORT_NOT_OPEN si ya se reviso, CANNOT_BAN_ADMIN si el denunciado es ADMIN. */
    UserReports reviewReport(UUID reportId, UUID adminId, ReportStatus status);

    /** Idempotente. Cierra como BANNED sus denuncias abiertas. 404 si no existe o esta borrado; 409 si es ADMIN. */
    void ban(UUID userId, UUID adminId);

    /** Idempotente. No reabre denuncias. Los follows siguen ahi: el ban no los borra. */
    void unban(UUID userId);

    Page<Users> findBanned(Pageable pageable);
}
