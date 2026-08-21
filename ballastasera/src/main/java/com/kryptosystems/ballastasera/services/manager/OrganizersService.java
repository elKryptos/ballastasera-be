package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrganizersService {
    List<Organizers> findAll();
    Organizers findById(UUID id);
    Organizers findBySlug(String slug);
    List<Organizers> findByUserId(UUID userId);
    Organizers save(Organizers organizer);

    /** Crea el perfil, isVerified=false por defecto: queda pendiente de aprobacion. */
    Organizers createForUser(UUID userId, OrganizerCreateDto dto);

    /** Actualiza datos del organizador que posee un usuario. */
    Organizers update(UUID id, UUID requesterId, OrganizerUpdateDto dto);

    void delete(UUID id, UUID requesterId);

    Page<Organizers> findPendingVerification(Pageable pageable);

    /** Aprueba: isVerified=true, sube el rol del dueno y le manda el email de notificacion. */
    Organizers verify(UUID id);

    /** Regresa los organizadores que ya han sido verificados. */
    Page<Organizers> findVerified(Pageable pageable);

    Organizers findVerifiedBySlug(String slug);

    void verifyExistsById(UUID id);

    /** Admin crea el perfil sin dueño, a partir de datos externos (ej. Instagram). Nace verified=true y claimed=false. */
    Organizers createUnclaimed(OrganizerCreateDto dto);

    /** Admin asigna el organizer a un usuario real: user=userId, claimed=true, promueve el rol y notifica por email. */
    Organizers claim(UUID organizerId, UUID userId);
}
