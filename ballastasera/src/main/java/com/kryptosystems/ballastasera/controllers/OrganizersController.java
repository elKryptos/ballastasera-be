package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizers")
@RequiredArgsConstructor
public class OrganizersController {

    private final OrganizersService organizersService;
    private final EventsMapper eventsMapper;

    /** Crea el perfil de organizador. Queda pendiente (isVerified=false) hasta
     * que un admin lo apruebe; solo entonces se pueden publicar eventos. */
    @PostMapping()
    public ResponseEntity<OrganizerDetailDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                     @Valid @RequestBody OrganizerCreateDto body
    ) {
        var organizer = organizersService.createForUser(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventsMapper.toOrganizerDetail(organizer));
    }
}
