package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** El usuario que envió una solicitud de organizador, tal como lo ve el admin en OrganizerRequestAdminDto. */
@Getter
@Setter
public class RequesterDto {
    private UUID userId;
    private String displayName;
    /** El email del login: a donde van los avisos de aprobado/rechazado. */
    private String email;
    private String avatarUrl;
    /** El Instagram de su perfil social, si lo tiene: el admin lo compara con el de la solicitud. */
    private String instagram;
    /** Fecha de registro (users.created_at): una cuenta de ayer pidiendo una página es una señal para el admin. */
    private OffsetDateTime memberSince;
}
