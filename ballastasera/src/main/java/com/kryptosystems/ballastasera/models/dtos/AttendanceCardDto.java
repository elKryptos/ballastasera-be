package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

/** Card de evento + el status de asistencia del usuario logueado, para "mis eventos". */
@Getter
@Setter
public class AttendanceCardDto {
    private EventCardDto event;
}