package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Se publica cuando una foto de perfil subida deja de usarse (cambiada, vuelta a la de Google o quitada).
 * UserAvatarListener la borra del bucket solo si la transaccion hace commit, igual que OrganizerLogoDeletedEvent.
 */
@Getter
@RequiredArgsConstructor
public class UserAvatarDeletedEvent {
    private final String avatarUrl;
}
