package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.FlyerStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload liviano para el marcador/card del mapa. No incluye descripcion
 * ni asistentes con nombre: eso vive en EventDetailDto y en el endpoint
 * de attendees, para no inflar la respuesta del mapa.
 */
@Getter
@Setter
public class EventCardDto {
    private UUID id;
    private String slug;
    private String title;
    private String flyerUrl;
    private FlyerStatus flyerStatus;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private boolean liveNow;
    private boolean free;
    private BigDecimal price;
    private String currency;
    private Double latitude;
    private Double longitude;
    private String address;
    private OrganizerSummaryDto organizer;
    private String venueName;
    private List<String> danceStyles;
    private long goingCount;
}