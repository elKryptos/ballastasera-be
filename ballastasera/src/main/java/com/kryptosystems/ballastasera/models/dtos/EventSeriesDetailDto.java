package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class EventSeriesDetailDto {

    private UUID id;
    private String title;
    private Set<DayOfWeek> recurrenceDays;
    private boolean active;
    private LocalDate generateUntil;
    private String description;
    private String flyerUrl;

    private String instagramUrl;
    private String whatsappUrl;

    private LocalTime startTime;
    private LocalTime endTime;

    private boolean isFree;
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
