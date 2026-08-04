package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenuesServiceImpl implements VenuesService {

    private final VenuesRepository venuesRepository;

    @Override
    public List<Venues> findAll() {
        return venuesRepository.findAll();
    }

    @Override
    public Venues findById(UUID id) {
        return venuesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found with id " + id));
    }

    @Override
    public List<Venues> findByCityId(Long cityId) {
        return venuesRepository.findByCityId(cityId);
    }

    @Override
    public List<Venues> findByOrganizerId(UUID organizerId) {
        return venuesRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Venues save(Venues venue) {
        return venuesRepository.save(venue);
    }

    @Override
    public void deleteById(UUID id) {
        venuesRepository.deleteById(id);
    }
}
