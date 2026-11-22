package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

/** GET /events/{id}/attendance/me: si el usuario logueado dijo «Ci vado» y si busca con quien bailar. */
@Getter
@Setter
public class EventAttendanceStatusDto {
    private boolean going;
    private boolean lookingForPartner;
}
