package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
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
    public List<Cities> findActive(){
        return citiesRepository.findAllByIsActiveTrueOrderByNameAsc();
    }

    @Override
    public Cities findActiveById(Long id) {
        return citiesRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("City not found with id " + id));
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
    @Transactional
    public void deactivate(Long id) {
        Cities city = findById(id);
        city.setActive(false);
    }


}
