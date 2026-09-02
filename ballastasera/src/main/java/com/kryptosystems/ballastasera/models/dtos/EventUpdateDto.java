package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.EventType;
import com.kryptosystems.ballastasera.validations.EventTimeRange;
import com.kryptosystems.ballastasera.validations.ValidEventTiming;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ValidEventTiming
public class EventUpdateDto implements EventTimeRange {

    private UUID venueId;
    private UUID seriesId;
    private Long cityId;
    private String title;
    private EventType eventType;
    private String description;
    private String flyerUrl;
    private String instagramUrl;
    private String whatsappUrl;

    @Future
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private Boolean isFree;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
    private String currency;

    private String address;
    private Double latitude;
    private Double longitude;
    private Set<Long> danceStyleIds;
}
