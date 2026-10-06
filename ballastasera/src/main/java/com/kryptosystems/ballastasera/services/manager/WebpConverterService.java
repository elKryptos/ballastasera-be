package com.kryptosystems.ballastasera.services.manager;

public interface WebpConverterService {

    /** Convierte a webp corriendo cwebp como subproceso aislado. */
    byte[] convertToWebp(byte[] content);

    /** Igual que convertToWebp(content) pero con un tope propio para el lado largo (ej. logos). */
    byte[] convertToWebp(byte[] content, int maxLongEdge);
}
