package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Cities;

import java.util.List;

public interface CitiesService {
    List<Cities> findAll();
    Cities findById(Long id);
    Cities findBySlug(String slug);
    Cities save(Cities city);
    void deleteById(Long id);
}
