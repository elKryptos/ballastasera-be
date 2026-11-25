package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Fila ligera de las listas de personas (seguidores, seguidos, amigos, bloqueados). */
@Getter
@Setter
public class UserSummaryDto {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    /** Relacion de quien pide la lista con esta persona (para el boton de cada fila). */
    private SocialRelation relation;
    /** false = perfil privado: sale (ej. alguien privado que te sigue), pero sin enlace al perfil ni boton para
     * seguirlo, porque los dos darian 404. Dejar de seguir y desbloquear funcionan igual. El FE muestra
     * «Profilo privato». */
    private boolean profilePublic;
}
