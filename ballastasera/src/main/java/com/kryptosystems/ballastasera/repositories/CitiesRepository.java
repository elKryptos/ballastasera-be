package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Cities;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface CitiesRepository extends JpaRepository<Cities, Long> {
    Optional<Cities> findBySlug(String slug);
    Optional<Cities> findByIdAndIsActiveTrue(Long id);
    List<Cities>  findAllByIsActiveTrueOrderByNameAsc();
}
