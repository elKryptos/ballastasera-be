package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private final EventsRepository eventsRepository;

    @Override
    public List<Events> findAll() {
        return eventsRepository.findAll();
    }

    @Override
    public Events findById(UUID id) {
        return eventsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id " + id));
    }

    @Override
    public Events findBySlug(String slug) {
        return eventsRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with slug " + slug));
    }

    @Override
    public List<Events> findByOrganizerId(UUID organizerId) {
        return eventsRepository.findByOrganizerId(organizerId);
    }

    @Override
    public List<Events> findByVenueId(UUID venueId) {
        return eventsRepository.findByVenueId(venueId);
    }

    @Override
    public List<Events> findBySeriesId(UUID seriesId) {
        return eventsRepository.findBySeriesId(seriesId);
    }

    @Override
    public List<Events> findUpcomingPublishedByCity(Long cityId) {
        return eventsRepository.findByCityIdAndStatusAndStartAtGreaterThanEqualOrderByStartAtAsc(
                cityId, EventStatus.PUBLISHED, OffsetDateTime.now());
    }

    @Override
    public Events save(Events event) {
        return eventsRepository.save(event);
    }

    @Override
    public void deleteById(UUID id) {
        eventsRepository.deleteById(id);
    }
}
