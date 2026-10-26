package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/** 409 del flujo «Diventa organizzatore». code lo traduce el frontend (organizer-requests-plan.md §4.6). */
@Getter
public class OrganizerRequestConflictException extends RuntimeException {

    public static final String PENDING_REQUEST_EXISTS = "PENDING_REQUEST_EXISTS";
    public static final String ORGANIZER_LIMIT_REACHED = "ORGANIZER_LIMIT_REACHED";
    public static final String REQUEST_NOT_PENDING = "REQUEST_NOT_PENDING";
    public static final String REQUEST_STILL_PENDING = "REQUEST_STILL_PENDING";

    private final String code;

    public OrganizerRequestConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}