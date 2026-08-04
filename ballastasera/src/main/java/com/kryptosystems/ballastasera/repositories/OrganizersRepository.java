package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Organizers;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizersRepository extends JpaRepository<Organizers, UUID> {
    Optional<Organizers> findBySlug(String slug);
    List<Organizers> findByUserId(UUID userId);
}
