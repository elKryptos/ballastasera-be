package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

/**
 * 429 de «Invita a ballare»: ya mando 10 invitaciones en las ultimas 24 horas. Es el limite de negocio, distinto del
 * RATE_LIMITED del filtro. retryAfterSeconds: cuando sale de la ventana la invitacion mas vieja.
 */
@Getter
public class InviteLimitException extends RuntimeException {

    public static final String INVITE_LIMIT = "INVITE_LIMIT";

    private final long retryAfterSeconds;

    public InviteLimitException(long retryAfterSeconds) {
        super("Invite limit reached, retry in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
