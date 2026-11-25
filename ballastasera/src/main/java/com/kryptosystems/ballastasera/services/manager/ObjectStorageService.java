package com.kryptosystems.ballastasera.services.manager;

import java.util.UUID;

public interface ObjectStorageService {

    void uploadEventFlyerRaw(UUID eventId, byte[] content);

    String uploadEventFlyerFinal(UUID eventId, byte[] webpContent);

    void deleteEventFlyerRaw(UUID eventId);

    void deleteEventFlyerFinal(UUID eventId);

    String uploadVenueLogo(UUID venueId, byte[] webpContent);

    void deleteVenueLogo(String logoUrl);

    /** ownerId: la solicitud (antes de aprobar) o el organizador (después). */
    String uploadOrganizerLogo(UUID ownerId, byte[] webpContent);

    void deleteOrganizerLogo(String logoUrl);

    /** Foto de perfil subida por el usuario: en el bucket de logos, con prefijo avatars/. */
    String uploadUserAvatar(UUID userId, byte[] webpContent);

    /** Solo borra fotos subidas con uploadUserAvatar: la URL de Google se ignora. */
    void deleteUserAvatar(String avatarUrl);
}
