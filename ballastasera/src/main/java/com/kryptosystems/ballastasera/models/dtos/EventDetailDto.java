package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.FlyerStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class EventDetailDto {
    private UUID id;
    private String slug;
    private String title;
    private String description;
    private String flyerUrl;
    private FlyerStatus flyerStatus;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private boolean liveNow;
    private boolean free;
    private BigDecimal price;
    private String currency;
    private String address;
    private Double latitude;
    private Double longitude;
    private String cityName;

    /** Ya resuelto con fallback: event.instagramUrl si existe, si no organizer.instagram. */
    private String instagramUrl;
    private String whatsappUrl;

    private OrganizerDetailDto organizer;
    private String venueName;
    private List<String> danceStyles;

    private long goingCount;
    private long interestedCount;
}