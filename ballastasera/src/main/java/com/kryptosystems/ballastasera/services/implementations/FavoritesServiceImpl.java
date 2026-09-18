package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.FavoritesService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoritesServiceImpl implements FavoritesService {

    private final FavoritesRepository favoritesRepository;
    private final UsersRepository usersRepository;
    private final EventsRepository eventsRepository;

    @Override
    public List<Favorites> findByUserId(UUID userId) {
        return favoritesRepository.findByUserId(userId);
    }

    @Override
    public List<Favorites> findByEventId(UUID eventId) {
        return favoritesRepository.findByEventId(eventId);
    }

    @Override
    @Transactional
    public void addFavorite(UUID userId, UUID eventId) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        if (favoritesRepository.existsById(id)) {
            return;
        }
        Favorites favorite = new Favorites();
        favorite.setUser(usersRepository.getReferenceById(userId));
        favorite.setEvent(eventsRepository.getReferenceById(eventId));
        favoritesRepository.save(favorite);
        eventsRepository.incrementLikesCount(eventId);
    }

    @Override
    @Transactional
    public void removeFavorite(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        if (favoritesRepository.existsById(id)) {
            favoritesRepository.deleteById(id);
            eventsRepository.decrementLikesCount(eventId);
        }
    }

    @Override
    public boolean existsByUserIdAndEventId(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        return favoritesRepository.existsById(id);
    }
}
