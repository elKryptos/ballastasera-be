package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.NotificationsActivity;
import com.kryptosystems.ballastasera.models.entities.Users;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Campanita: «ti segue», «ora siete amici» e invitaciones (estas las crea InvitesService). Los avisos se borran a los
 * 60 dias (NotificationsCleanupJob). Silenciados, baneados y serate terminadas se filtran al leer.
 */
public interface NotificationsService {

    /** cursorAt/cursorId: fecha e id del ultimo aviso de la pagina anterior (null = primera). */
    NotificationsActivity findNotifications(UUID recipientId, OffsetDateTime cursorAt, UUID cursorId, int limit);

    /** El numero sobre la campanita: los visibles sin leer. */
    long countUnread(UUID recipientId);

    /** Al abrir la campanita: todos leidos. */
    void markAllRead(UUID recipientId);

    /**
     * follower empezo a seguir a followed. friendship: followed ya seguia a follower, asi que ahora son amigos y el
     * aviso es «ora siete amici» (con email si followed lo quiere, follower no es privado y followed no lo
     * silencio); si no, «ti segue». Sin
     * repetir el mismo aviso a
     * la misma pareja en 7 dias. Tiene que llamarse dentro de la transaccion del follow.
     */
    void notifyFollow(Users follower, Users followed, boolean friendship);

    /**
     * actorId dejo de seguir a recipientId: borra su «ti segue» sin leer. El «ora siete amici» se queda: si se borrara,
     * seguir y dejar de seguir en loop mandaria un email nuevo cada vez (el filtro de 7 dias ya no lo encontraria).
     */
    void deleteUnreadNewFollower(UUID actorId, UUID recipientId);

    /** Bloqueo: borra todos los avisos entre los dos, en los dos sentidos. */
    void deleteBetween(UUID userA, UUID userB);
}
