package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Una solicitud vista por el admin: lista y detalle de /rest/admin/organizer-requests.
 * Todo lo de OrganizerRequestDto más quién la pidió, quién la revisó y el contexto para decidir.
 */
@Getter
@Setter
public class OrganizerRequestAdminDto extends OrganizerRequestDto {
    /** «Richiesta da Kryptos». */
    private RequesterDto requester;
    /** Solo CLAIM: el organizador pedido, para que el admin lo compare con los datos de prueba. */
    private OrganizerSummaryDto targetOrganizer;
    /** Solo CLAIM: los venues que el organizador pedido ya tiene enlazados (D11). Lista vacía en NEW. */
    private List<VenuesSummaryDto> targetOrganizerVenues;
    private String reviewedByName;
    /** Rechazos anteriores de este usuario: con 1 o más, el admin ve «2º intento». */
    private long previousRejections;
}
