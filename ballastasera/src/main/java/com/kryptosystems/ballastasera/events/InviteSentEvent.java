package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.enums.InvitePhrase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Se publica con cada invitacion nueva, si el invitado tiene «Ricevi email». El email lo manda SocialEmailListener
 * solo si la transaccion hace commit. Lleva valores, no entidades.
 */
@Getter
@RequiredArgsConstructor
public class InviteSentEvent {
    private final String toEmail;
    private final String inviterName;
    private final UUID eventId;
    private final String eventTitle;
    private final OffsetDateTime eventStartAt;
    private final InvitePhrase phrase;
}
