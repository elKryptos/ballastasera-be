package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Cities;

import java.util.List;

public interface CitiesService {
    List<Cities> findAll();
    List<Cities> findActive();
    Cities findActiveById(Long id);
    Cities findById(Long id);
    Cities findBySlug(String slug);
    Cities save(Cities city);
    void deactivate(Long id);
}
