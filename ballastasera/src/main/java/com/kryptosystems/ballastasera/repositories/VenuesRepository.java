package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Venues;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VenuesRepository extends JpaRepository<Venues, UUID> {
    List<Venues> findByCityId(Long cityId);
    List<Venues> findByOrganizerId(UUID organizerId);
}
