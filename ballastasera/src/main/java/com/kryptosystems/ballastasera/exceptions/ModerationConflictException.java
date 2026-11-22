package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/** 409 de denuncias y ban. code lo traduce el frontend del admin. */
@Getter
public class ModerationConflictException extends RuntimeException {

    /** La denuncia ya se reviso (DISMISSED o BANNED). */
    public static final String REPORT_NOT_OPEN = "REPORT_NOT_OPEN";
    /** No se banea a un ADMIN (ni a uno mismo, que tambien es ADMIN). */
    public static final String CANNOT_BAN_ADMIN = "CANNOT_BAN_ADMIN";

    private final String code;

    public ModerationConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
