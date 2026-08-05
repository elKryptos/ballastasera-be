package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.services.manager.FavoritesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoritesServiceImpl implements FavoritesService {

    private final FavoritesRepository favoritesRepository;

    @Override
    public List<Favorites> findByUserId(UUID userId) {
        return favoritesRepository.findByUserId(userId);
    }

    @Override
    public List<Favorites> findByEventId(UUID eventId) {
        return favoritesRepository.findByEventId(eventId);
    }

    @Override
    public Favorites save(Favorites favorite) {
        return favoritesRepository.save(favorite);
    }

    @Override
    public void deleteById(UserEventId id) {
        favoritesRepository.deleteById(id);
    }
}
