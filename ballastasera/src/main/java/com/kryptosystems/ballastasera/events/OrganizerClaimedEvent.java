package com.kryptosystems.ballastasera.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Se publica al asignar un organizador a un usuario (claim). El email lo manda OrganizerEmailListener
 * solo si la transacción hace commit. Lleva valores y no la entidad: el listener corre después del
 * commit, sin una sesión de Hibernate fiable para cargar relaciones.
 */
@Getter
@RequiredArgsConstructor
public class OrganizerClaimedEvent {
    private final String toEmail;
    private final String organizerName;
}