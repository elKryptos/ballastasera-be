package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class EventSeriesCreateDto {

    @NotNull
    private UUID organizerId;
    private UUID venueId;

    @NotNull
    private Long cityId;

    @NotBlank
    private String title;

    @NotBlank
    private String rrule;

    private String description;
    private String flyerUrl;
    private String instagramUrl;
    private String whatsappUrl;

    private boolean isFree = true;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
    private String currency = "EUR";

    @NotBlank
    private String address;
    private Double latitude;
    private Double longitude;

    @NotNull
    private LocalTime startTime;
    private LocalTime endTime;

    private Set<Long> danceStyleIds;
}
