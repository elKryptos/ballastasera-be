package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DanceStylesRepository extends JpaRepository<DanceStyles, Long> {
    Optional<DanceStyles> findBySlug(String slug);
}
