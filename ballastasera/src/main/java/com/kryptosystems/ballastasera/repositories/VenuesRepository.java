package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Venues;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenuesRepository extends JpaRepository<Venues, UUID> {
    List<Venues> findByCityId(Long cityId);
    List<Venues> findByOrganizerId(UUID organizerId);
    Optional<Venues> findByCityIdAndNameIgnoreCase(Long cityId, String name);
    List<Venues> findByCityIdAndNameContainingIgnoreCase(Long cityId, String name);
    Optional<Venues> findByCityIdAndNameIgnoreCaseAndIdNot(Long cityId, String name, UUID id);
}
