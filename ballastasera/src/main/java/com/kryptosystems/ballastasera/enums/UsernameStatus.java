package com.kryptosystems.ballastasera.enums;

/** Si un username se puede elegir, para el campo «Nome utente» mientras se escribe. El propio cuenta como AVAILABLE. */
public enum UsernameStatus {
    AVAILABLE,
    INVALID,    // no cumple el formato (UsernameUtils.REGEX)
    RESERVED,   // admin, ballastasera... (UsernameUtils.RESERVED)
    TAKEN       // lo tiene otra persona
}
