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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.kryptosystems.ballastasera.utilities.RestConstants.EVENTS;

@RestController
@RequestMapping(EVENTS)
@RequiredArgsConstructor
public class EventsController {

    private static final String GET_MAP_EVENTS = "";
    private static final String GET_EVENT_DETAIL = "/{id}";
    private static final String CREATE = "";
    private static final String UPDATE = "/{id}";
    private static final String UPDATE_STATUS = "/{id}/status";
    private static final String DELETE = "/{id}";
    private static final String REMOVE_VENUE = "/{id}/venue";
    private static final String GET_ATTENDEES = "/{id}/attendees";
    private static final String SET_ATTENDANCE = "/{id}/attendance";
    private static final String REMOVE_ATTENDANCE = "/{id}/attendance";
    private static final String ADD_FAVORITE = "/{id}/favorite";
    private static final String REMOVE_FAVORITE = "/{id}/favorite";
    private static final String IS_FAVORITE = "/{id}/favorite";
    private static final String UPDATE_FLYER = "/{id}/flyer";
    private static final String DELETE_FLYER = "/{id}/flyer";

    private final EventsService eventsService;
    private final EventAttendanceService eventAttendanceService;
    private final FavoritesService favoritesService;
    private final EventsRepository eventsRepository;

    /** Marcadores del mapa: solo eventos publicados, en vivo o por empezar,
     * dentro del bounding box visible. Nunca devuelve eventos pasados. */
    @GetMapping(GET_MAP_EVENTS)
    public ResponseEntity<List<EventCardDto>> getMapEvents(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLng,
            @RequestParam double maxLng,
            @RequestParam(required = false) Long cityId
    ) {
        return ResponseEntity.ok(eventsService.findMapEvents(minLat, maxLat, minLng, maxLng, cityId));
    }

    /** Publico */
    @GetMapping(GET_EVENT_DETAIL)
    public ResponseEntity<EventDetailDto> getEventDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(eventsService.getEventDetail(id));
    }

    /** Requiere estar autenticado. El organizerId del body debe pertenecer al
     * usuario logueado y ese organizer debe estar verificado. Nace en PENDING. */
    @PostMapping(CREATE)
    public ResponseEntity<EventDetailDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody EventCreateDto body) {
        var event = eventsService.create(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventsService.toEventDetailDto(event));
    }

    /** Requiere estar autenticado y ser dueño del evento (via organizer.user.id). */
    @PatchMapping(UPDATE)
    public ResponseEntity<EventDetailDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody EventUpdateDto body) {
        var event = eventsService.update(id, principal.getId(), body);
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

    /** Requiere estar autenticado y ser dueño del evento. Publicar / despublicar / cancelar. */
    @PatchMapping(UPDATE_STATUS)
    public ResponseEntity<EventDetailDto> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody EventStatusUpdateDto body) {
        var event = eventsService.updateStatus(principal.getId(), id, body.getStatus());
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

    @DeleteMapping(DELETE)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        eventsService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado */
    @DeleteMapping(REMOVE_VENUE)
    public ResponseEntity<EventDetailDto> removeVenue(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        var event = eventsService.removeVenue(id, principal.getId());
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

    /** Público. Solo quienes marcaron "voy" Y activaron mostrar su perfil publicamente.
     * El conteo total de "van" (EventDetailDto.goingCount) es independiente
     * de esta lista y siempre incluye a todos, con o sin opt-in. */
    @GetMapping(GET_ATTENDEES)
    public ResponseEntity<Page<AttendeeDto>> getAttendees(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(eventAttendanceService.findPublicGoingAttendees(id, PageRequest.of(page, size)));
    }

    /** Requiere estar autenticato. Marca "GOING" o "INTERESTED". Idempotente: repetir con otro status lo actualiza. */
    @PostMapping(SET_ATTENDANCE)
    public ResponseEntity<Void> setAttendance(
            @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequestDto body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        eventAttendanceService.setAttendance(principal.getId(), id, body.getStatus());
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Elimina la marca "GOING" o "INTERESTED". La lista de asistentes se actualiza con -1 */
    @DeleteMapping(REMOVE_ATTENDANCE)
    public ResponseEntity<Void> removeAttendance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        eventAttendanceService.removeAttendance(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Usuario marca evento como favorito */
    @PostMapping(ADD_FAVORITE)
    public ResponseEntity<Void> addFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        favoritesService.addFavorite(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado */
    @DeleteMapping(REMOVE_FAVORITE)
    public ResponseEntity<Void> removeFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        favoritesService.removeFavorite(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado */
    @GetMapping(IS_FAVORITE)
    public ResponseEntity<Boolean> isFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(favoritesService.existsByUserIdAndEventId(principal.getId(), id));
    }

    /** Requiere autenticacion y ownership. La conversion a WebP se ejecuta de forma asincrona. */
    @PatchMapping(value = UPDATE_FLYER, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventDetailDto> updateFlyer(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable UUID id,
                                                      @RequestParam("file") MultipartFile file) {
        var event = eventsService.updateFlyer(id, principal.getId(), file);
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

    /** Requiere estar autenticado y ser dueño del evento. */
    @DeleteMapping(DELETE_FLYER)
    public ResponseEntity<EventDetailDto> deleteFlyer(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID id) {
        var event = eventsService.deleteFlyer(id, principal.getId());
        return ResponseEntity.ok(eventsService.toEventDetailDto(event));
    }

}
