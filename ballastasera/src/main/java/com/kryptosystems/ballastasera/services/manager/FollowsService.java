package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.CursorPageDto;
import com.kryptosystems.ballastasera.models.dtos.SocialCounts;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Seguir es abierto (sin solicitud). Amigos = se siguen los dos. */
public interface FollowsService {

    /** Idempotente. 400 si es uno mismo; 404 si el perfil es privado, borrado, baneado o hay bloqueo. */
    void follow(UUID followerId, UUID followedId);

    /** Idempotente y sin chequeos de visibilidad: se puede dejar de seguir a quien se volvio privado. */
    void unfollow(UUID followerId, UUID followedId);

    /** Silenciar o reactivar a quien sigues: sale de tu feed y de tu campanita, y no se entera. 404 si no lo sigues. */
    void setMuted(UUID followerId, UUID followedId, boolean muted);

    /** ¿followerId sigue a followedId y lo tiene silenciado? false si no lo sigue. */
    boolean isMuted(UUID followerId, UUID followedId);

    /** Relacion de viewerId con userId. NONE si son la misma persona. */
    SocialRelation findRelation(UUID viewerId, UUID userId);

    SocialCounts countSocial(UUID userId);

    /** cursorAt/cursorId: fecha del follow e id de la ultima fila de la pagina anterior (null = primera). */
    CursorPageDto<UserSummaryDto> findFollowers(UUID userId, OffsetDateTime cursorAt, UUID cursorId, int limit);

    CursorPageDto<UserSummaryDto> findFollowing(UUID userId, OffsetDateTime cursorAt, UUID cursorId, int limit);

    /** q filtra por nombre (contiene, sin distinguir mayusculas). null o vacio = todos. */
    CursorPageDto<UserSummaryDto> findFriends(UUID userId, String q, OffsetDateTime cursorAt, UUID cursorId, int limit);
}
