package com.kryptosystems.ballastasera.services.manager;

public interface WebpConverterService {

    /** Convierte a webp corriendo cwebp como subproceso aislado. */
    byte[] convertToWebp(byte[] content);
}
