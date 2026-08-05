package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.DanceStyles;

import java.util.List;

public interface DanceStylesService {
    List<DanceStyles> findAll();
    DanceStyles findById(Long id);
    DanceStyles findBySlug(String slug);
    DanceStyles save(DanceStyles danceStyle);
    void deleteById(Long id);
}
