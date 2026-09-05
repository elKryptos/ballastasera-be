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

/** organizerId lo manda el cliente (un mismo usuario puede tener varios
 * Organizers), pero el service verifica que ese organizer le pertenezca
 * al principal autenticado antes de guardar nada. */
@Getter
@Setter
@ValidEventTiming
public class EventCreateDto implements EventTimeRange {

    @NotNull
    private UUID organizerId;

    private UUID venueId;
    private UUID seriesId;

    @NotNull
    private Long cityId;

    @NotBlank
    private String title;
    private EventType eventType = EventType.EVENT;
    private String description;
    private String flyerUrl;
    private String instagramUrl;
    private String whatsappUrl;

    @NotNull
    @Future
    private OffsetDateTime startAt;

    @NotNull
    private OffsetDateTime endAt;
    private boolean isFree = true;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
    private String currency = "EUR";

    @NotBlank
    private String address;
    private Double latitude;
    private Double longitude;
    private Set<Long> danceStyleIds;
}
