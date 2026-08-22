package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.EventSeriesCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesUpdateDto;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.kryptosystems.ballastasera.utilities.RestConstants.EVENT_SERIES;

@RestController
@RequestMapping(EVENT_SERIES)
@RequiredArgsConstructor
public class EventSeriesController {

    private static final String GET_EVENT_SERIES_DETAIL = "/{id}";
    private static final String CREATE = "";
    private static final String UPDATE = "/{id}";
    private static final String DELETE = "/{id}";
    private static final String REMOVE_VENUE = "/{id}/venue";

    private final EventSeriesService eventSeriesService;

    /** Público. Detalle de una serie (título, rrule, venue, precio, dance styles...). */
    @GetMapping(GET_EVENT_SERIES_DETAIL)
    public ResponseEntity<EventSeriesDetailDto> getEventSeriesDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(eventSeriesService.getEventSeriesDetail(id));
    }

    /** Requiere estar autenticado. El organizerId del body debe pertenecer al usuario logueado
     * y ese organizer debe estar verificado. */
    @PostMapping(CREATE)
    public ResponseEntity<EventSeriesDetailDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody EventSeriesCreateDto body) {
        var series = eventSeriesService.create(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventSeriesService.toEventSeriesDetailDto(series));
    }

    /** Requiere estar autenticado y ser dueño de la serie (via organizer.user.id). */
    @PatchMapping(UPDATE)
    public ResponseEntity<EventSeriesDetailDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody EventSeriesUpdateDto body) {
        var series = eventSeriesService.update(id, principal.getId(), body);
        return ResponseEntity.ok(eventSeriesService.toEventSeriesDetailDto(series));
    }

    /** Requiere estar autenticado y ser dueño de la serie.*/
    @DeleteMapping(DELETE)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        eventSeriesService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado y ser dueño de la serie.*/
    @DeleteMapping(REMOVE_VENUE)
    public ResponseEntity<EventSeriesDetailDto> removeVenue(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        var series = eventSeriesService.removeVenue(id, principal.getId());
        return ResponseEntity.ok(eventSeriesService.toEventSeriesDetailDto(series));
    }

}
