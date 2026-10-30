package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Se publica cuando el admin le quita el organizador a su dueño (unclaim). El release no lo publica:
 * lo decide el propio dueño. Lleva valores y no la entidad, igual que OrganizerClaimedEvent.
 */
@Getter
@RequiredArgsConstructor
public class OrganizerUnclaimedEvent {
    private final String toEmail;
    private final String organizerName;
}