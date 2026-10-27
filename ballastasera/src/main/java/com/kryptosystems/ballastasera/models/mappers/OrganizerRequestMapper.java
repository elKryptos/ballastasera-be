package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.enums.OrganizerType;
import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapea las solicitudes «Diventa organizzatore» entre entidad y DTOs.
 * uses: reutiliza toOrganizerSummaryDto (OrganizerMapper) para targetOrganizer y
 * toVenueSummaryDto (VenuesMapper) para targetOrganizerVenues, sin duplicarlos aquí.
 * Ojo: por el uses también se aplica VenuesMapper.blankToNull a cada String→String ("" o "   " → null,
 * el resto con strip). No declarar otro blankToNull aquí: MapStruct da error de método ambiguo.
 * Las relaciones que leen los to...Dto (organizer, venue, venueCity, user, reviewedBy) llegan ya
 * cargadas por los JOIN FETCH de OrganizerRequestsRepository: sin ellos habría N+1 en la lista del admin.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {OrganizerMapper.class, VenuesMapper.class},
        imports = OrganizerRequestType.class)
public interface OrganizerRequestMapper {

    /** Para el autor de la solicitud (GET /me y respuesta del create). Los campos con el mismo nombre
     * en entidad y DTO los copia MapStruct solo; aquí van los que salen de una relación o se calculan. */
    @Mapping(target = "organizerName", expression = "java(resolveOrganizerName(request))")
    @Mapping(target = "organizerType", expression = "java(resolveOrganizerType(request))")
    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "organizerSlug", source = "organizer.slug")
    @Mapping(target = "venueCityName", source = "venueCity.name")
    @Mapping(target = "venueId", source = "venue.id")
    @Mapping(target = "venueName", source = "venue.name")
    OrganizerRequestDto toOrganizerRequestDto(OrganizerRequests request);

    /** Para la lista del admin. Hereda los @Mapping de toOrganizerRequestDto (el parámetro tiene que
     * llamarse request en los dos, las expression lo usan). targetOrganizer solo en CLAIM: en un NEW
     * aprobado organizer también está relleno, pero es el organizador creado, no una página pedida.
     * previousRejections y targetOrganizerVenues necesitan queries: los añade la sobrecarga de abajo. */
    @InheritConfiguration(name = "toOrganizerRequestDto")
    @Mapping(target = "requester", source = "user")
    @Mapping(target = "targetOrganizer", source = "organizer",
             conditionExpression = "java(request.getType() == OrganizerRequestType.CLAIM)")
    @Mapping(target = "reviewedByName", source = "reviewedBy.displayName")
    @Mapping(target = "previousRejections", ignore = true)
    @Mapping(target = "targetOrganizerVenues", ignore = true)
    OrganizerRequestAdminDto toOrganizerRequestAdminDto(OrganizerRequests request);

    /** Detalle del admin: el controller pide al servicio los rechazos anteriores del usuario y los venues
     * de la página pedida (List.of() en NEW) y los pasa aquí, así el servicio solo devuelve entidades. */
    default OrganizerRequestAdminDto toOrganizerRequestAdminDto(OrganizerRequests request, long previousRejections,
                                                                 List<Venues> targetOrganizerVenues) {
        OrganizerRequestAdminDto dto = toOrganizerRequestAdminDto(request);
        dto.setPreviousRejections(previousRejections);
        dto.setTargetOrganizerVenues(toVenueSummaryDtos(targetOrganizerVenues));
        return dto;
    }

    /** MapStruct lo implementa llamando a VenuesMapper.toVenueSummaryDto por cada venue. */
    List<VenuesSummaryDto> toVenueSummaryDtos(List<Venues> venues);

    /** requester del AdminDto. memberSince = fecha de registro. */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "memberSince", source = "createdAt")
    RequesterDto toRequesterDto(Users user);

    /** Solo copia campos escalares (type, name, organizerType, description, instagram, contactEmail, phone,
     * website, facebook, message, venueAddress). status se queda en el PENDING por defecto de la entidad.
     * user, organizer (targetOrganizerId), venueCity (venueCityId), logoUrl y termsAcceptedAt los pone el
     * service, igual que las reglas de negocio: en CLAIM vaciar los campos de perfil y la dirección (D6),
     * normalizar instagram con InstagramUtils. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "venue", ignore = true)
    @Mapping(target = "venueCity", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    @Mapping(target = "termsAcceptedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "rejectionMessage", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "acknowledgedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OrganizerRequests toOrganizerRequestEntity(OrganizerRequestCreateDto dto);

    /** Approve de una NEW: el organizador nuevo con los datos de la solicitud (name, description, logoUrl,
     * website, phone, contactEmail, instagram ya normalizado, facebook). id, user y updatedAt se ignoran a
     * propósito: existen con el mismo nombre en la solicitud y MapStruct los copiaría. slug, verified,
     * claimed y user los pone el service. */
    @Mapping(target = "type", source = "organizerType")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "verified", ignore = true)
    @Mapping(target = "claimed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "venues", ignore = true)
    @Mapping(target = "eventSeries", ignore = true)
    @Mapping(target = "events", ignore = true)
    Organizers toOrganizerEntity(OrganizerRequests request);

    /** NEW: el nombre propuesto · CLAIM: el de la página pedida (la solicitud no guarda nombre).
     * NEW aprobada: el del organizador creado, que es el mismo. */
    default String resolveOrganizerName(OrganizerRequests request) {
        return request.getOrganizer() != null ? request.getOrganizer().getName() : request.getName();
    }

    /** NEW: el tipo propuesto · CLAIM: el de la página pedida (la solicitud no guarda tipo). */
    default OrganizerType resolveOrganizerType(OrganizerRequests request) {
        return request.getOrganizer() != null ? request.getOrganizer().getType() : request.getOrganizerType();
    }
}
