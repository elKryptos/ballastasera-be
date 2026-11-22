package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/** 400 de las acciones sociales (seguir, bloquear, denunciar). code lo traduce el frontend. */
@Getter
public class SocialActionException extends RuntimeException {

    /** Seguirse, bloquearse o denunciarse a uno mismo. */
    public static final String SELF_ACTION = "SELF_ACTION";

    private final String code;

    public SocialActionException(String code, String message) {
        super(message);
        this.code = code;
    }
}
