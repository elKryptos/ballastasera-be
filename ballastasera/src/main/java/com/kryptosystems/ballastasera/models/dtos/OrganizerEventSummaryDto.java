package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.EventType;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter
public class OrganizerEventSummaryDto {
    private UUID id;
    private String slug;
    private String title;
    private EventType eventType;
    private EventStatus status;
    private String flyerUrl;
    private FlyerStatus flyerStatus;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private String cityName;
    private String venueName;

}
