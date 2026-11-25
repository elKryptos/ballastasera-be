package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.ReportReason;
import com.kryptosystems.ballastasera.enums.ReportStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Fila de la cola de denuncias del admin (GET /rest/admin/reports). Quien denuncia y el denunciado en campos planos. */
@Getter
@Setter
public class UserReportAdminDto {
    private UUID id;
    private ReportReason reason;
    private String note;
    private ReportStatus status;
    private OffsetDateTime createdAt;

    private UUID reporterId;
    private String reporterName;
    private String reporterAvatarUrl;

    private UUID reportedId;
    private String reportedName;
    private String reportedAvatarUrl;
    private String reportedEmail;
    /** Ya baneado (por otra denuncia o con un ban directo). */
    private boolean reportedBanned;
    /** Denuncias abiertas contra el denunciado, esta incluida si sigue abierta. */
    private long openReportsAgainst;

    /** null mientras esta OPEN. */
    private String reviewedByName;
    private OffsetDateTime reviewedAt;
}
