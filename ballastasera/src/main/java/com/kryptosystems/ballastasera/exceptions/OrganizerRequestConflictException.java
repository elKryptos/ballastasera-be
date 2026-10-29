package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/** 409 del flujo «Diventa organizzatore». code lo traduce el frontend (organizer-requests-plan.md §4.6). */
@Getter
public class OrganizerRequestConflictException extends RuntimeException {

    public static final String PENDING_REQUEST_EXISTS = "PENDING_REQUEST_EXISTS";
    public static final String ORGANIZER_LIMIT_REACHED = "ORGANIZER_LIMIT_REACHED";
    public static final String REQUEST_NOT_PENDING = "REQUEST_NOT_PENDING";
    public static final String REQUEST_STILL_PENDING = "REQUEST_STILL_PENDING";
    /** Approve: el venue que pasa el admin ya está enlazado a otro organizador. */
    public static final String VENUE_ALREADY_LINKED = "VENUE_ALREADY_LINKED";
    /** Approve: el tipo exige venue (D11) y el organizador no tiene ninguno. */
    public static final String VENUE_REQUIRED = "VENUE_REQUIRED";
    /** Approve/reject: otro admin revisó la misma solicitud a la vez (@Version). No lo lanza el servicio:
     * lo usa el handler de ObjectOptimisticLockingFailureException, porque el conflicto salta en el commit. */
    public static final String CONCURRENT_UPDATE = "CONCURRENT_UPDATE";

    private final String code;

    public OrganizerRequestConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}