package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.EventType;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter
public class OrganizerEventDetailDto {
    private UUID id;
    private String slug;
    private String title;
    private EventType eventType;
    private EventStatus status;
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
    private Long cityId;
    private UUID venueId;
    private UUID seriesId;
    private String instagramUrl;
    private String whatsappUrl;
    private List<DanceStyleDto> danceStyles;
    private OrganizerDetailDto organizer;
    private String venueName;
    private String cityName;
    private long goingCount;
    private long interestedCount;

}
