package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
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

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenuesController {

    private final VenuesService venuesService;
    private final VenuesMapper venuesMapper;

    /** Público. Busca si el venue existe en la base de datos. Si esta hace autocomplete (FE)*/
    @GetMapping()
    public ResponseEntity<List<VenuesSummaryDto>> getVenues(@RequestParam Long cityId,
                                                           @RequestParam(required = false) String search) {
        return ResponseEntity.ok(venuesService.search(cityId, search).stream()
                .map(venuesMapper::toVenueSummary)
                .toList());
    }

    /** Requiere estar autenticado. El organizerId tiene que estar verificado y el venue creado es reusable por todos los organizers. */
    @PostMapping()
    public ResponseEntity<VenuesSummaryDto> createVenue(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody VenueCreateDto body) {
        var venue = venuesService.create(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(venuesMapper.toVenueSummary(venue));
    }
}
