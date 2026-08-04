package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.repositories.DanceStylesRepository;
import com.kryptosystems.ballastasera.services.manager.DanceStylesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DanceStylesServiceImpl implements DanceStylesService {

    private final DanceStylesRepository danceStylesRepository;

    @Override
    public List<DanceStyles> findAll() {
        return danceStylesRepository.findAll();
    }

    @Override
    public DanceStyles findById(Long id) {
        return danceStylesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dance style not found with id " + id));
    }

    @Override
    public DanceStyles findBySlug(String slug) {
        return danceStylesRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Dance style not found with slug " + slug));
    }

    @Override
    public DanceStyles save(DanceStyles danceStyle) {
        return danceStylesRepository.save(danceStyle);
    }

    @Override
    public void deleteById(Long id) {
        danceStylesRepository.deleteById(id);
    }
}
