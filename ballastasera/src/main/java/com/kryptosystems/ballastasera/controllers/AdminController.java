package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrganizersService organizersService;
    private final OrganizerMapper organizerMapper;
    private final VenuesService venuesService;

    /** Lista de organizadores pendientes de verificacion por un admin. */
    @GetMapping("/organizers/pending")
    public ResponseEntity<Page<OrganizerDetailDto>> getPending(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        Page<OrganizerDetailDto> result = organizersService
                .findPendingVerification(PageRequest.of(page, size))
                .map(organizerMapper::toOrganizerDetail);
        return ResponseEntity.ok(result);
    }

    /** Aprueba: isVerified=true, sube el rol del usuario y envia el email de notificacion. */
    @PatchMapping("/organizers/{id}/verify")
    public ResponseEntity<OrganizerDetailDto> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(organizerMapper.toOrganizerDetail(organizersService.verify(id)));
    }

    /** Borra un venue. Solo ADMIN — el service valida que no tenga eventos activos. */
    @DeleteMapping("/venues/{id}")
    public ResponseEntity<Void> deleteVenue(@PathVariable UUID id) {
        venuesService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
