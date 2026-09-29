package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Respuesta del mapa de eventos. truncated = true si se llego al limite y
 * pueden faltar eventos lejanos: el FE sugiere acercar el zoom.
 */
@Getter
@Setter
public class MapEventsDto {
    private List<EventCardDto> events;
    private boolean truncated;
}
