package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Se publica cuando alguien empieza a seguir a otra persona sin que queden amigos, si quien lo recibe tiene «Ricevi
 * email». El email «ti segue» lo manda SocialEmailListener solo si la transaccion hace commit. Lleva valores, no
 * entidades.
 */
@Getter
@RequiredArgsConstructor
public class NewFollowerEvent {
    private final String toEmail;
    private final UUID followerId;
    private final String followerName;
}
