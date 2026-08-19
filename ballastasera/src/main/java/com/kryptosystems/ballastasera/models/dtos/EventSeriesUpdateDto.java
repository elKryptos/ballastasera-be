package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class EventSeriesUpdateDto {

    private UUID venueId;
    private Long cityId;
    private String title;
    private String rrule;
    private String description;
    private String flyerUrl;
    private String instagramUrl;
    private String whatsappUrl;

    private Boolean isFree;

    @DecimalMin(value = "0.0", inclusive = true)
    private Double price;
    private String currency;

    private String address;
    private Double latitude;
    private Double longitude;

    private LocalTime startTime;
    private LocalTime endTime;

    private Set<Long> danceStyleIds;
}
