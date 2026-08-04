package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FavoritesRepository extends JpaRepository<Favorites, UserEventId> {
    List<Favorites> findByUserId(UUID userId);
    List<Favorites> findByEventId(UUID eventId);
}
