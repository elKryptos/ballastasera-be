package com.kryptosystems.ballastasera.services.manager;

import java.util.UUID;

public interface EventFlyerProcessingService {

    /** Convierte a webp en background y publica el resultado. Bean separado
     * a proposito: @Async solo funciona a traves del proxy de Spring, una
     * auto-invocacion desde EventsServiceImpl lo ignoraria silenciosamente. */
    void convertAndPublish(UUID eventId, byte[] rawContent);
}
