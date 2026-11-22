package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

/**
 * Body opcional de POST /events/{id}/attendance. Sin body (el FE de antes) o con lookingForPartner null,
 * la marca no cambia: false al crear el «Ci vado», la que tenia si ya iba.
 */
@Getter
@Setter
public class EventAttendanceUpdateDto {
    /** «Cerco qualcuno con cui ballare» para esta serata. */
    private Boolean lookingForPartner;
}
