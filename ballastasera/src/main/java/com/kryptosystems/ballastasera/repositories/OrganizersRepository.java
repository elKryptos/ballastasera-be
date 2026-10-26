package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Organizers;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizersRepository extends JpaRepository<Organizers, UUID> {
    Optional<Organizers> findBySlug(String slug);

    Optional<Organizers> findBySlugAndIsVerifiedTrue(String slug);

    boolean existsByIdAndIsVerifiedTrue(UUID id);

    List<Organizers> findByUserId(UUID userId);

    Page<Organizers> findByIsVerifiedFalse(Pageable pageable);

    Page<Organizers> findByIsVerifiedTrue(Pageable pageable);

    long countByUserId(UUID userId); //cuántos organizadores tiene el usuario (límite MAX_ORGANIZERS_PER_USER)

    /** Buscador del wizard (CLAIM). UserIsNull además de ClaimedFalse: un organizador creado por su dueño y
     * verificado sigue con claimed=false. Containing escapa % y _ del texto del usuario. */
    List<Organizers> findByIsVerifiedTrueAndClaimedFalseAndUserIsNullAndNameContainingIgnoreCaseOrderByNameAsc(String name,
                                                                                                             Limit limit);

}