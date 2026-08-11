package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class EventSeriesSummaryDto {
    private UUID id;
    private String title;
    private String rrule;
}
