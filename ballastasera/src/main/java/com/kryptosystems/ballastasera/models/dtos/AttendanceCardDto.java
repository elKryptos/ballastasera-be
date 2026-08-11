package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import lombok.Getter;
import lombok.Setter;

/** Card de evento + el status de asistencia del usuario logueado, para "mis eventos". */
@Getter
@Setter
public class AttendanceCardDto {
    private EventCardDto event;
    private AttendanceStatus status;
}