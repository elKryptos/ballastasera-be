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

    /** Actualiza datos del organizador que posee un usuario. */
    Organizers update(UUID id, UUID requesterId, OrganizerUpdateDto dto);

    Organizers updateAsAdmin(UUID id, OrganizerUpdateDto dto);

    void delete(UUID id, UUID requesterId);

    void deleteAsAdmin(UUID id);

    /** D13: el dueño deja un organizador que le llegó por CLAIM o se lo asignó el admin (user=null,
     * claimed=false) y baja a USER si no le quedan. Nunca borra: si lo creó él con una NEW, 403
     * y tiene que usar delete. */
    void release(UUID id, UUID requesterId);

    /** D13: el admin le quita el organizador a su dueño, como release, y le avisa por email.
     * 409 si no tiene dueño o si su dueño lo creó con una NEW (esos se borran con deleteAsAdmin). */
    Organizers unclaimAsAdmin(UUID id);

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

    /** Buscador del wizard (CLAIM): organizadores verificados y sin dueño cuyo nombre contiene name, por orden alfabético. */
    List<Organizers> findClaimable(String name, int limit);
}
