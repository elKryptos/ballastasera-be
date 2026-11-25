package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Se publica cuando alguien devuelve el follow y quedan amigos, si quien lo recibe tiene «Ricevi email». El email
 * «ora siete amici» lo manda SocialEmailListener solo si la transaccion hace commit. Lleva valores, no entidades.
 */
@Getter
@RequiredArgsConstructor
public class FriendshipCreatedEvent {
    private final String toEmail;
    private final UUID friendId;
    private final String friendName;
}
