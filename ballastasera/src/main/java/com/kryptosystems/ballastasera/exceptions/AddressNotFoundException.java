package com.kryptosystems.ballastasera.exceptions;

/** Direccion que el geocoding no logro resolver en coordenadas. */
public class AddressNotFoundException extends RuntimeException{
    public AddressNotFoundException(String message) {
        super(message);
    }
}
