package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Organizers;

import java.util.List;
import java.util.UUID;

public interface OrganizersService {
    List<Organizers> findAll();
    Organizers findById(UUID id);
    Organizers findBySlug(String slug);
    List<Organizers> findByUserId(UUID userId);
    Organizers save(Organizers organizer);
    void deleteById(UUID id);
}
