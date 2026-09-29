package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.VenueMapPinDto;
import com.kryptosystems.ballastasera.models.dtos.VenuesSummaryDto;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.kryptosystems.ballastasera.utilities.RestConstants.VENUES;

/** Solo lectura: los venues los crea y edita el admin (ver AdminController). */
@RestController
@RequestMapping(VENUES)
@RequiredArgsConstructor
public class VenuesController {

    private static final String GET_VENUES = "";
    private static final String GET_MAP_VENUES = "/map";

    private final VenuesService venuesService;
    private final VenuesMapper venuesMapper;

    /** Público. Busca si el venue existe en la base de datos. Si está hace autocomplete (FE)*/
    @GetMapping(GET_VENUES)
    public ResponseEntity<List<VenuesSummaryDto>> getVenues(@RequestParam Long cityId,
                                                           @RequestParam(required = false) String search) {
        return ResponseEntity.ok(venuesService.search(cityId, search).stream()
                .map(venuesMapper::toVenueSummaryDto)
                .toList());
    }

    /** Público. Todos los venues de una ciudad para los pines del mapa.
     * El FE lo llama solo cuando se activa la capa de lugares, y filtra por
     * tipo y por area visible en memoria. */
    @GetMapping(GET_MAP_VENUES)
    public ResponseEntity<List<VenueMapPinDto>> getMapVenues(@RequestParam Long cityId) {
        return ResponseEntity.ok(venuesService.findByCityId(cityId).stream()
                .map(venuesMapper::toVenueMapPinDto)
                .toList());
    }

}
