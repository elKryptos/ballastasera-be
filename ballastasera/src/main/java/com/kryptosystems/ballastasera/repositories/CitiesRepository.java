package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Cities;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CitiesRepository extends JpaRepository<Cities, Long> {
    Optional<Cities> findBySlug(String slug);
}