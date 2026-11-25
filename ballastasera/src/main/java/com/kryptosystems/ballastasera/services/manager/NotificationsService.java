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
     * aviso es «ora siete amici»; si no, «ti segue». Los dos con email si followed lo quiere y follower no es privado
     * («ora siete amici», ademas, si followed no lo silencio). Sin repetir el mismo aviso (ni su email) a la misma
     * pareja en 7 dias. Tiene que llamarse dentro de la transaccion del follow.
     */
    void notifyFollow(Users follower, Users followed, boolean friendship);

    /** Bloqueo: borra todos los avisos entre los dos, en los dos sentidos. */
    void deleteBetween(UUID userA, UUID userB);
}
