package com.kryptosystems.ballastasera.services.manager;

import java.util.UUID;

public interface ObjectStorageService {

    void uploadEventFlyerRaw(UUID eventId, byte[] content);

    String uploadEventFlyerFinal(UUID eventId, byte[] webpContent);

    void deleteEventFlyerRaw(UUID eventId);

    void deleteEventFlyerFinal(UUID eventId);
}
