package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.EventSeries;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventSeriesServiceImpl implements EventSeriesService {

    private final EventSeriesRepository eventSeriesRepository;

    @Override
    public List<EventSeries> findAll() {
        return eventSeriesRepository.findAll();
    }

    @Override
    public EventSeries findById(UUID id) {
        return eventSeriesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event series not found with id " + id));
    }

    @Override
    public List<EventSeries> findByOrganizerId(UUID organizerId) {
        return eventSeriesRepository.findByOrganizerId(organizerId);
    }

    @Override
    public EventSeries save(EventSeries series) {
        return eventSeriesRepository.save(series);
    }

    @Override
    public void deleteById(UUID id) {
        eventSeriesRepository.deleteById(id);
    }
}
