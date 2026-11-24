package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/** 409 de PATCH /users/me/username. code lo traduce el frontend («Già in uso», «Questo nome è riservato»). */
@Getter
public class UsernameUnavailableException extends RuntimeException {

    /** Lo tiene otra persona, también si dos lo pidieron a la vez (índice único users_username_key). */
    public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    /** Está en UsernameUtils.RESERVED. */
    public static final String USERNAME_RESERVED = "USERNAME_RESERVED";

    private final String code;

    public UsernameUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }
}
