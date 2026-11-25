package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Fila de «Utenti bannati» en el admin (GET /rest/admin/users/banned), con el boton «Sbanna». */
@Getter
@Setter
public class BannedUserAdminDto {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String email;
    /** null en los baneados antes de la fase 4. */
    private OffsetDateTime bannedAt;
}
