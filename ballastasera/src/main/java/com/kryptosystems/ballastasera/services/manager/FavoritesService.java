package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;

import java.util.List;
import java.util.UUID;

public interface FavoritesService {
    List<Favorites> findByUserId(UUID userId);
    List<Favorites> findByEventId(UUID eventId);
    Favorites addFavorite(UUID userId, UUID eventId);
    void removeFavorite(UUID userId, UUID eventId);
    /** usado para el corazon de favoritos */
    boolean existsByUserIdAndEventId(UUID userId, UUID eventId);
}
