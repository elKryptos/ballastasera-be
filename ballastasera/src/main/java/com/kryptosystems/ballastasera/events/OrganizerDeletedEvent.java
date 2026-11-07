package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Se publica cuando el admin elimina un organizador que tenía dueño. El deleteByOwner no lo publica:
 * lo decide el propio dueño. Lleva valores y no la entidad: tras el commit la entidad ya no existe.
 */
@Getter
@RequiredArgsConstructor
public class OrganizerDeletedEvent {
    private final String toEmail;
    private final String organizerName;
}
