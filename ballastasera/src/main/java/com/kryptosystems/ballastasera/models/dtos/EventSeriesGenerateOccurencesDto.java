package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EventSeriesGenerateOccurencesDto {

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}
