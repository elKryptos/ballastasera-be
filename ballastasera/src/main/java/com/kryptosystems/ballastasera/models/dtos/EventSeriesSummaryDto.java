package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class EventSeriesSummaryDto {

    private UUID id;
    private String title;
    private Set<DayOfWeek> recurrenceDays;
    private String flyerUrl;

    private LocalTime startTime;
    private LocalTime endTime;

    private boolean isFree;
    private BigDecimal price;
    private String currency;

    private String address;
    private String cityName;
    private String venueName;

    private OrganizerSummaryDto organizer;
    private List<String> danceStyles;
}
