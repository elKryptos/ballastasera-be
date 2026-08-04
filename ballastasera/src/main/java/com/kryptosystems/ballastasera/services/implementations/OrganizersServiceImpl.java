package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizersServiceImpl implements OrganizersService {

    private final OrganizersRepository organizersRepository;

    @Override
    public List<Organizers> findAll() {
        return organizersRepository.findAll();
    }

    @Override
    public Organizers findById(UUID id) {
        return organizersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + id));
    }

    @Override
    public Organizers findBySlug(String slug) {
        return organizersRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with slug " + slug));
    }

    @Override
    public List<Organizers> findByUserId(UUID userId) {
        return organizersRepository.findByUserId(userId);
    }

    @Override
    public Organizers save(Organizers organizer) {
        return organizersRepository.save(organizer);
    }

    @Override
    public void deleteById(UUID id) {
        organizersRepository.deleteById(id);
    }
}
