package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Se publica cuando un logo de organizador deja de usarse: solicitud cancelada o rechazada, logo cambiado,
 * organizador borrado. OrganizerLogoListener lo borra del bucket solo si la transacción hace commit: con un
 * rollback la fila sigue apuntando al archivo y no se puede haber borrado (organizer-requests-plan.md §5.3).
 */
@Getter
@RequiredArgsConstructor
public class OrganizerLogoDeletedEvent {
    private final String logoUrl;
}
