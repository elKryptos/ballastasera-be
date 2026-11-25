package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Users;

import java.util.List;
import java.util.UUID;

/** Bloquear vale en los dos sentidos: ninguno ve el perfil del otro ni puede seguirlo. */
public interface BlocksService {

    /** Idempotente. Borra los follows en los dos sentidos. 400 si es uno mismo; 404 si no existe o esta borrado. */
    void block(UUID blockerId, UUID blockedId);

    /** Idempotente. Los follows borrados al bloquear no vuelven. */
    void unblock(UUID blockerId, UUID blockedId);

    /** Los mas recientes primero. */
    List<Users> findBlocked(UUID blockerId);
}
