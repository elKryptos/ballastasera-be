package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.ProfileActivity;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.data.domain.Slice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserActivityService {

    List<UUID> findMyFavoritesEventsIds(UUID userId);
    /** past = false: en curso o futuros (ASC). past = true: terminados (DESC).
     * cursorStartAt/cursorId = null para la primera pagina. */
    Slice<Events> findMyFavorites(UUID userId, boolean past, OffsetDateTime cursorStartAt, UUID cursorId, int limit);
    long countMyFavorites(UUID userId, boolean past);

    /** Mismo criterio que findMyFavorites. Sirve tambien para las serate de otro usuario, despues de
     * comprobar canSeeAttendanceOf. */
    Slice<Events> findMyAttendance(UUID userId, boolean past, OffsetDateTime cursorStartAt, UUID cursorId, int limit);
    long countMyAttendance(UUID userId, boolean past);

    /** Si viewerId puede ver las serate de userId (attendanceVisibility y relacion). 404 si el perfil no se
     * puede ver (privado, borrado, baneado o bloqueo), como GET /users/{id}. */
    boolean canSeeAttendanceOf(UUID viewerId, UUID userId);

    /** «Avete in comune» y el pallino «Cerco qualcuno» del perfil de user visto por viewerId. */
    ProfileActivity findProfileActivity(UUID viewerId, Users user, SocialRelation relation);
}
