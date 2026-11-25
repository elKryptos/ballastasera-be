package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/** 400 de las acciones sociales (seguir, bloquear, denunciar, invitar). code lo traduce el frontend. */
@Getter
public class SocialActionException extends RuntimeException {

    /** Seguirse, bloquearse, denunciarse o invitarse a uno mismo. */
    public static final String SELF_ACTION = "SELF_ACTION";
    /** Invitar a alguien que no es amigo (no se siguen los dos). */
    public static final String NOT_FRIENDS = "NOT_FRIENDS";
    /** Quien invita tiene el perfil privado: un perfil privado no invita ni lo invitan. */
    public static final String PROFILE_PRIVATE = "PROFILE_PRIVATE";
    /** El amigo invitado tiene el perfil privado. */
    public static final String FRIEND_PRIVATE = "FRIEND_PRIVATE";
    /** Invitar a una serata a la que uno no dijo «Ci vado». */
    public static final String NOT_GOING = "NOT_GOING";
    /** La serata no esta publicada o ya termino. */
    public static final String EVENT_NOT_INVITABLE = "EVENT_NOT_INVITABLE";
    /** El amigo ya dijo «Ci vado» (solo si sus serate no estan ocultas a todos). */
    public static final String ALREADY_GOING = "ALREADY_GOING";

    private final String code;

    public SocialActionException(String code, String message) {
        super(message);
        this.code = code;
    }
}
