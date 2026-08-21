package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.models.mappers.EventSeriesMapper;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizers")
@RequiredArgsConstructor
public class OrganizersController {

    private final OrganizersService organizersService;
    private final OrganizerMapper organizerMapper;
    private final EventsService eventService;
    private final EventsMapper eventsMapper;
    private final VenuesService venueService;
    private final VenuesMapper venuesMapper;
    private final EventSeriesService eventSeriesService;
    private final EventSeriesMapper eventSeriesMapper;

    /** Requiere estar autenticado. Crea el perfil de organizador. Queda pendiente (isVerified=false) hasta
     * que un admin lo apruebe; solo entonces se pueden publicar eventos. */
    @PostMapping()
    public ResponseEntity<OrganizerDetailDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                     @Valid @RequestBody OrganizerCreateDto body) {
        var organizer = organizersService.createForUser(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(organizerMapper.toOrganizerDetailDto(organizer));
    }

    /** Público, nadie necesita loguearse. Sirve para la página de perfil del organizador
     * que esté verificado que ve cualquier visitante (como el detalle de un evento). */
    @GetMapping("/{slug}")
    public ResponseEntity<OrganizerDetailDto> getBySlug(@PathVariable String slug) {
        var organizer = organizersService.findVerifiedBySlug(slug);
        return ResponseEntity.ok(organizerMapper.toOrganizerDetailDto(organizer));
    }

    /** Requiere estar autenticado. El mismo usuario. Para que el dueño vea su(s) propio(s)
     * perfil(es) de organizador, típicamente al entrar a su panel/dashboard. */
    @GetMapping("/me")
    public ResponseEntity<List<OrganizerDetailDto>> getMyOrganizers(@AuthenticationPrincipal UserPrincipal principal) {
        var organizers = organizersService.findByUserId(principal.getId());
        return ResponseEntity.ok(organizers.stream().map(organizerMapper::toOrganizerDetailDto).toList());
    }

    /** Requiere estar autenticado. Editar el propio perfil de organizador. No estoy actulizando
     * el Slug ya creado para no romper los link ya compartidos, necesario poner un mensaje en
     * el FE para avisar de la decision tomada en el BE */
    @PatchMapping("/{id}")
    public ResponseEntity<OrganizerDetailDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody OrganizerUpdateDto body) {
        var organizer = organizersService.update(id, principal.getId(), body);
        return ResponseEntity.ok(organizerMapper.toOrganizerDetailDto(organizer));
    }

    /** Público, directorio general (como listar eventos), solo muestra los ya verificados
     * para no exponer perfiles pendientes de aprobación. */
    @GetMapping("")
    public ResponseEntity<Page<OrganizerSummaryDto>> getOrganizersList(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        var organizers = organizersService.findVerified(PageRequest.of(page, size))
                .map(organizerMapper::toOrganizerSummaryDto);
        return ResponseEntity.ok(organizers);
    }

    /** Público. Lista los eventos publicados por un organizador, para su página de perfil. */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<EventCardDto>> getOrganizerEvents(@PathVariable UUID id) {
        organizersService.verifyExistsById(id);
        List<EventCardDto> events = eventService.findPublishedByOrganizerId(id)
                .stream()
                .map(eventsMapper::toEventCardDto)
                .toList();
        return ResponseEntity.ok(events);
    }

    /** Público. Lista los venues publicados por un organizador. */
    @GetMapping("/{id}/venues")
    public ResponseEntity<List<VenuesSummaryDto>> getOrganizerVenues(@PathVariable UUID id) {
        organizersService.verifyExistsById(id);
        var venues = venueService.findByOrganizerId(id).stream()
                .map(venuesMapper::toVenueSummaryDto)
                .toList();
        return ResponseEntity.ok(venues);
    }

    /** Público. Lista los event-series publicados por un organizador. */
    @GetMapping("/{id}/event-series")
    public ResponseEntity<List<EventSeriesSummaryDto>> getOrganizerEventSeries(@PathVariable UUID id) {
        organizersService.verifyExistsById(id);
        var series = eventSeriesService.findByOrganizerId(id).stream()
                .map(eventSeriesMapper::toEventSeriesSummaryDto)
                .toList();
        return ResponseEntity.ok(series);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrganizer(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        organizersService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
