package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/organizers")
@RequiredArgsConstructor
public class AdminOrganizersController {

    private final OrganizersService organizersService;
    private final EventsMapper eventsMapper;

    /** Cola de organizadores pendientes de verificacion. */
    @GetMapping("/pending")
    public ResponseEntity<Page<OrganizerDetailDto>> getPending(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        Page<OrganizerDetailDto> result = organizersService
                .findPendingVerification(PageRequest.of(page, size))
                .map(eventsMapper::toOrganizerDetail);
        return ResponseEntity.ok(result);
    }

    /** Aprueba: isVerified=true, sube el rol del usuario y envia el email de notificacion. */
    @PatchMapping("/{id}/verify")
    public ResponseEntity<OrganizerDetailDto> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(eventsMapper.toOrganizerDetail(organizersService.verify(id)));
    }
}
