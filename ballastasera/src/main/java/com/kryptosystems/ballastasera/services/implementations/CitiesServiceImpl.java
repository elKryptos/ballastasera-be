package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CitiesServiceImpl implements CitiesService {

    private final CitiesRepository citiesRepository;

    @Override
    public List<Cities> findAll() {
        return citiesRepository.findAll();
    }

    @Override
    public Cities findById(Long id) {
        return citiesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("City not found with id " + id));
    }

    @Override
    public Cities findBySlug(String slug) {
        return citiesRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("City not found with slug " + slug));
    }

    @Override
    public Cities save(Cities city) {
        return citiesRepository.save(city);
    }

    @Override
    public void deleteById(Long id) {
        citiesRepository.deleteById(id);
    }
}
