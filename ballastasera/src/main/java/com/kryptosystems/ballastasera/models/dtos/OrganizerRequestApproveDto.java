package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Body opcional de PATCH /rest/admin/organizer-requests/{id}/approve. */
@Getter
@Setter
public class OrganizerRequestApproveDto {
    /** NEW de tipo local (D11): el venue que el admin acaba de crear con la dirección propuesta
     *  (POST /rest/admin/venues, sin organizerId). El approve lo enlaza al organizador creado.
     *  null en CLAIM (el venue ya está enlazado) y en tipos sin local. */
    private UUID venueId;
}
