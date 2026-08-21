package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.*;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrganizersService organizersService;
    private final OrganizerMapper organizerMapper;
    private final VenuesService venuesService;
    private final EventsService eventsService;
    private final EventSeriesService eventSeriesService;
    private final VenuesMapper venuesMapper;

    /** Lista de organizadores pendientes de verificacion por un admin. */
    @GetMapping("/organizers/pending")
    public ResponseEntity<Page<OrganizerDetailDto>> getPending(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        Page<OrganizerDetailDto> result = organizersService
                .findPendingVerification(PageRequest.of(page, size))
                .map(organizerMapper::toOrganizerDetailDto);
        return ResponseEntity.ok(result);
    }

    /** Aprueba: isVerified=true, sube el rol del usuario y envia el email de notificacion. */
    @PatchMapping("/organizers/{id}/verify")
    public ResponseEntity<OrganizerDetailDto> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(organizerMapper.toOrganizerDetailDto(organizersService.verify(id)));
    }

    /** Admin crea un organizer a partir de datos externos (Instagram, etc.), sin dueño todavia. */
    @PostMapping( "/organizers")
    public ResponseEntity<OrganizerDetailDto> createUnclaimedOrganizer(@Valid @RequestBody OrganizerCreateDto body) {
        var organizer = organizersService.createUnclaimed(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(organizerMapper.toOrganizerDetailDto(organizer));
    }

    /** Admin asigna el organizer al usuario que lo reclamo. */
    @PatchMapping("/organizers/{id}/claim")
    public ResponseEntity<OrganizerDetailDto> claimOrganizer(@PathVariable UUID id,
                                                             @Valid @RequestBody OrganizerClaimDto body) {
        var organizer = organizersService.claim(id, body.getUserId());
        return ResponseEntity.ok(organizerMapper.toOrganizerDetailDto(organizer));
    }

    /** Admin crea un evento para cualquier organizer (reclamado o no), sin chequeo de ownership. */
    @PostMapping( "/events")
    public ResponseEntity<EventDetailDto> createEvent(@Valid @RequestBody EventCreateDto body) {
        var event = eventsService.createAsAdmin(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventsService.toEventDetailDto(event));
    }

    /** Admin crea una serie de eventos para cualquier organizer, sin chequeo de ownership. */
    @PostMapping( "/event-series")
    public ResponseEntity<EventSeriesDetailDto> createEventSeries(@Valid @RequestBody EventSeriesCreateDto body) {
        var series = eventSeriesService.createAsAdmin(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventSeriesService.toEventSeriesDetailDto(series));
    }

    /** Admin crea un venue para cualquier organizer, sin chequeo de ownership. */
    @PostMapping("/venues")
    public ResponseEntity<VenueDetailDto> createVenue(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody VenueCreateDto body) {
        var venue = venuesService.createAsAdmin(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(venuesMapper.toVenueDetailDto(venue));
    }

    /** Borra un venue. Solo ADMIN — el service valida que no tenga eventos activos. */
    @DeleteMapping("/venues/{id}")
    public ResponseEntity<Void> deleteVenue(@PathVariable UUID id) {
        venuesService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
