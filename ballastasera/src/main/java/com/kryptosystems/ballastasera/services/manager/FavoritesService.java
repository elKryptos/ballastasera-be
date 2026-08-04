package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;

import java.util.List;
import java.util.UUID;

public interface FavoritesService {
    List<Favorites> findByUserId(UUID userId);
    List<Favorites> findByEventId(UUID eventId);
    Favorites save(Favorites favorite);
    void deleteById(UserEventId id);
}
