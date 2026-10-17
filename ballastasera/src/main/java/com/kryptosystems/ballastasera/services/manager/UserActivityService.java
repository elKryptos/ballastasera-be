package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Events;
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

    /** Mismo criterio que findMyFavorites. */
    Slice<Events> findMyAttendance(UUID userId, boolean past, OffsetDateTime cursorStartAt, UUID cursorId, int limit);
    long countMyAttendance(UUID userId, boolean past);
}
