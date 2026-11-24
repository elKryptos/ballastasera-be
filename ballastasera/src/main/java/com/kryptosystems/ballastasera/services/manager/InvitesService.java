package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.InvitePhrase;
import com.kryptosystems.ballastasera.models.dtos.InviteFriendsActivity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * «Invita a ballare»: solo entre amigos, ninguno con el perfil privado, a una serata no terminada a la que va quien
 * invita. 10 en las ultimas 24 horas, 1 por persona y serata. Sin respuesta: el aviso lleva a la serata.
 */
public interface InvitesService {

    /**
     * Idempotente: volver a invitar a la misma persona a la misma serata no hace nada ni cuenta para el limite.
     * 400 (SocialActionException) si no se puede, 404 si el invitado o la serata no existen, 429
     * (InviteLimitException) si ya mando 10 en 24 horas.
     */
    void invite(UUID inviterId, UUID inviteeId, UUID eventId, InvitePhrase phrase);

    /** Dialogo de la serata: los amigos de inviterId (como /me/friends, q filtra por nombre) con su estado. Los mismos
     * 400 que invite si quien invita es privado, la serata no se puede invitar o no va (PROFILE_PRIVATE,
     * EVENT_NOT_INVITABLE, NOT_GOING); 404 si la serata no existe. */
    InviteFriendsActivity findInviteFriends(UUID inviterId, UUID eventId, String q, OffsetDateTime cursorAt,
                                            UUID cursorId, int limit);
}
