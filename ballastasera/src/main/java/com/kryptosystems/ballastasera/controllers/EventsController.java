package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.AttendanceRequestDto;
import com.kryptosystems.ballastasera.models.dtos.AttendeeDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventsController {

    private final EventsService eventsService;
    private final EventAttendanceService eventAttendanceService;

    /**
     * Marcadores del mapa: solo eventos publicados, en vivo o por empezar,
     * dentro del bounding box visible. Nunca devuelve eventos pasados.
     */
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

    /**
     * Solo quienes marcaron "voy" Y activaron mostrar su perfil publicamente.
     * El conteo total de "van" (EventDetailDto.goingCount) es independiente
     * de esta lista y siempre incluye a todos, con o sin opt-in.
     */
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
}