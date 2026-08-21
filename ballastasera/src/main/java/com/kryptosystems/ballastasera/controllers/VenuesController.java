package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.VenuesSummaryDto;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenuesController {

    private final VenuesService venuesService;
    private final VenuesMapper venuesMapper;

    /** Público. Busca si el venue existe en la base de datos. Si está hace autocomplete (FE)*/
    @GetMapping()
    public ResponseEntity<List<VenuesSummaryDto>> getVenues(@RequestParam Long cityId,
                                                           @RequestParam(required = false) String search) {
        return ResponseEntity.ok(venuesService.search(cityId, search).stream()
                .map(venuesMapper::toVenueSummaryDto)
                .toList());
    }

    /** Requiere estar autenticado. El organizerId tiene que estar verificado y el venue creado es reusable por todos los organizers. */
    @PostMapping()
    public ResponseEntity<VenueDetailDto> createVenue(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody VenueCreateDto body) {
        var venue = venuesService.create(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(venuesMapper.toVenueDetailDto(venue));
    }

    /** Requiere estar autenticado y ser dueño del venue.... cambiar a que solo ADMIN puede hacer modificaciones */
    @PatchMapping("/{id}")
    public ResponseEntity<VenueDetailDto> updateVenue(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody VenueUpdateDto body) {
        var venue = venuesService.update(id, principal.getId(), body);
        return ResponseEntity.ok(venuesMapper.toVenueDetailDto(venue));
    }

}
