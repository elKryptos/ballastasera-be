package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.*;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.FavoritesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventsController {

    private final EventsService eventsService;
    private final EventAttendanceService eventAttendanceService;
    private final FavoritesService favoritesService;
    private final EventsRepository eventsRepository;

    /** Marcadores del mapa: solo eventos publicados, en vivo o por empezar,
     * dentro del bounding box visible. Nunca devuelve eventos pasados. */
    @GetMapping
    public ResponseEntity<List<EventCardDto>> getMapEvents(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLng,
            @RequestParam double maxLng,
            @RequestParam(required = false) Long cityId
    ) {
        return ResponseEntity.ok(eventsService.findMapEvents(minLat, maxLat, minLng, maxLng, cityId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailDto> getEventDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(eventsService.getEventDetail(id));
    }

    /** Requiere estar autenticado. El organizerId del body debe pertenecer al
     * usuario logueado y ese organizer debe estar verificado. Nace en PENDING. */
    @PostMapping()
    public ResponseEntity<EventDetailDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody EventCreateDto body) {
        var event = eventsService.create(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventsService.toEventDetailDto(event));
    }

    /** Requiere estar autenticado y ser dueño del evento (via organizer.user.id). */
    @PatchMapping("/{id}")
    public ResponseEntity<EventDetailDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody EventUpdateDto body) {
        var event = eventsService.update(id, principal.getId(), body);
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

    /** Requiere estar autenticado y ser dueño del evento. Publicar / despublicar / cancelar. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<EventDetailDto> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody EventStatusUpdateDto body) {
        var event = eventsService.updateStatus(principal.getId(), id, body.getStatus());
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        eventsService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Solo quienes marcaron "voy" Y activaron mostrar su perfil publicamente.
     * El conteo total de "van" (EventDetailDto.goingCount) es independiente
     * de esta lista y siempre incluye a todos, con o sin opt-in. */
    @GetMapping("/{id}/attendees")
    public ResponseEntity<Page<AttendeeDto>> getAttendees(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(eventAttendanceService.findPublicGoingAttendees(id, PageRequest.of(page, size)));
    }

    /** Marca "GOING" o "INTERESTED". Idempotente: repetir con otro status lo actualiza. */
    @PostMapping("/{id}/attendance")
    public ResponseEntity<Void> setAttendance(
            @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequestDto body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        eventAttendanceService.setAttendance(principal.getId(), id, body.getStatus());
        return ResponseEntity.noContent().build();
    }

    /** Elimina la marca "GOING" o "INTERESTED". La lista de asistentes se actualiza con -1 */
    @DeleteMapping("/{id}/attendance")
    public ResponseEntity<Void> removeAttendance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        eventAttendanceService.removeAttendance(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Usuario marca evento como favorito */
    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> addFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        favoritesService.addFavorite(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> removeFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        favoritesService.removeFavorite(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/favorite")
    public ResponseEntity<Boolean> isFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(favoritesService.existsByUserIdAndEventId(principal.getId(), id));
    }

}