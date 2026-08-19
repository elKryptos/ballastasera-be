package com.kryptosystems.ballastasera.exceptions;

import lombok.Getter;

import java.util.UUID;

@Getter
public class DuplicateVenueException extends RuntimeException {

    private final UUID existingVenueId;

    public DuplicateVenueException(String message, UUID existingVenueId) {
        super(message);
        this.existingVenueId = existingVenueId;
    }
}
