package com.kryptosystems.ballastasera.security;

/**
 * Grupo de limite de una request. Cada grupo tiene su propio bucket por usuario (con login) o por IP
 * (sin login). Lo decide RateLimitFilter.resolveGroup; los limites estan en application.properties.
 */
public enum RateLimitGroup {
    /** GET y HEAD. */
    READ,
    /** POST, PATCH, PUT y DELETE que no caen en otro grupo. */
    WRITE,
    /** Login con Google (/oauth2/**, /login/**). Siempre por IP: todavia no hay usuario. */
    AUTH,
    /** Acciones sociales (seguir, bloquear, denunciar, invitar): limite por minuto y por dia. */
    SOCIAL,
    /** Subidas multipart (flyer, logo, avatar). */
    UPLOAD
}
