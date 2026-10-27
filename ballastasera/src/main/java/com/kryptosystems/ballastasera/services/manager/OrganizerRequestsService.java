package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestCreateDto;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrganizerRequestsService {

    /** Wizard «Diventa organizzatore»: crea la solicitud NEW o CLAIM en PENDING. */
    OrganizerRequests create(UUID userId, OrganizerRequestCreateDto dto);

    /** La solicitud más reciente del usuario; vacío si nunca pidió (el controller responde 204). */
    Optional<OrganizerRequests> findMyLatest(UUID userId);

    /** El dueño retira su solicitud PENDING: pasa a CANCELLED. */
    void cancel(UUID requestId, UUID userId);

    /** El dueño ha visto el resultado (APPROVED/REJECTED/CANCELLED): fija acknowledgedAt una sola vez. */
    OrganizerRequests acknowledge(UUID requestId, UUID userId);

    /** Cola del admin por estado. El orden lo decide el servicio (sección 5.1), no quien llama. */
    Page<OrganizerRequests> findByStatus(OrganizerRequestStatus status, Pageable pageable);

    /** Detalle del admin, con user, organizer, reviewedBy, venue y venueCity cargados. */
    OrganizerRequests findById(UUID requestId);

    /** Rechazos anteriores del autor, sin contar esta solicitud si es REJECTED («2º intento»). */
    long countPreviousRejections(OrganizerRequests request);
}