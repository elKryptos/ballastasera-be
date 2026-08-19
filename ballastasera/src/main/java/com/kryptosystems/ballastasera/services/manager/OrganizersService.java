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
    // metodo insicuro
    //void deleteById(UUID id);
    void delete(UUID id, UUID requesterId);

    /** Crea el perfil, isVerified=false por defecto: queda pendiente de aprobacion. */
    Organizers createForUser(UUID userId, OrganizerCreateDto dto);

    Page<Organizers> findPendingVerification(Pageable pageable);

    /** Aprueba: isVerified=true, sube el rol del dueno y le manda el email de notificacion. */
    Organizers verify(UUID id);

    /** Actualiza datos del organizador que posee un usuario. */
    Organizers update(UUID id, UUID requesterId, OrganizerUpdateDto dto);

    /** Regresa los organizadores que ya han sido verificados. */
    Page<Organizers> findVerified(Pageable pageable);

    Organizers findVerifiedBySlug(String slug);

    Organizers findVerifiedById(UUID id);
}
