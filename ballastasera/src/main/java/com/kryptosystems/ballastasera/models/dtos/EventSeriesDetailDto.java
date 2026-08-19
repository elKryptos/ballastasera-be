package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class EventSeriesDetailDto {

    private UUID id;
    private String title;
    private String rrule;
    private String description;
    private String flyerUrl;

    private String instagramUrl;
    private String whatsappUrl;

    private LocalTime startTime;
    private LocalTime endTime;

    private boolean free;
    private BigDecimal price;
    private String currency;

    private String address;
    private Double latitude;
    private Double longitude;
    private String cityName;
    private String venueName;

    private OrganizerDetailDto organizer;
    private List<String> danceStyles;
}
