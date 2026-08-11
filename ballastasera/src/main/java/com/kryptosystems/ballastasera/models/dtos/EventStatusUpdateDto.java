package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.EventStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventStatusUpdateDto {

    @NotNull
    private EventStatus status;
}
