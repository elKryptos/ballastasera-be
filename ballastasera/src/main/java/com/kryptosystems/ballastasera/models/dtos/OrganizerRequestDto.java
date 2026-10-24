package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;
import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.enums.OrganizerType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una solicitud vista por su autor: GET /rest/organizer-requests/me y respuesta del create.
 * El frontend la usa para la tarjeta del menú según el estado (en revisión, rechazada con motivo,
 * aprobada → bienvenida).
 */
@Getter
@Setter
public class OrganizerRequestDto {
    private UUID id;
    private OrganizerRequestType type;
    private OrganizerRequestStatus status;

    /** NEW: el nombre propuesto · CLAIM: el nombre de la página pedida. Para «Salsa Loca Milano · inviata oggi». */
    private String organizerName;
    /** NEW: el tipo propuesto · CLAIM: el tipo de la página pedida. */
    private OrganizerType organizerType;
    /** CLAIM: la página pedida · NEW aprobada: el organizador creado. null en NEW sin aprobar. */
    private UUID organizerId;
    /** Para enlazar al perfil público una vez aprobada. */
    private String organizerSlug;

    private String description;
    private String logoUrl;
    private String instagram;
    private String contactEmail;
    private String phone;
    private String website;
    private String facebook;
    private String message;

    /** Solo NEW de tipo local: la dirección propuesta… */
    private String venueAddress;
    private String venueCityName;
    /** …y el venue que el admin creó al aprobar (null mientras está PENDING). */
    private UUID venueId;
    private String venueName;

    /** Solo REJECTED: el motivo elegido por el admin y su mensaje opcional (obligatorio si el motivo es OTHER). */
    private OrganizerRejectReason rejectionReason;
    private String rejectionMessage;

    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    /** Cuándo vio el usuario la bienvenida o el rechazo. null → el frontend todavía tiene que mostrarlo. */
    private OffsetDateTime acknowledgedAt;
}
