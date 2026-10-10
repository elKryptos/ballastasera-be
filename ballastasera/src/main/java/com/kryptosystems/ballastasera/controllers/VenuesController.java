package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.cache.VenuesCache;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueMapPinDto;
import com.kryptosystems.ballastasera.models.dtos.VenuesSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.kryptosystems.ballastasera.utilities.RestConstants.VENUES;

/** Solo lectura: los venues los crea y edita el admin (ver AdminController). */
@RestController
@RequestMapping(VENUES)
@RequiredArgsConstructor
public class VenuesController {

    private static final String GET_VENUES = "";
    private static final String GET_MAP_VENUES = "/map";
    private static final String GET_VENUE_DETAIL = "/{id}";

    private final VenuesCache venuesCache;

    /** Público. Busca si el venue existe en la base de datos. Si está hace autocomplete (FE)*/
    @GetMapping(GET_VENUES)
    public ResponseEntity<List<VenuesSummaryDto>> getVenues(@RequestParam Long cityId,
                                                            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(venuesCache.search(cityId, search));
    }

    /** Público. Todos los venues de una ciudad para los pines del mapa.
     * El FE lo llama solo cuando se activa la capa de lugares, y filtra por
     * tipo y por area visible en memoria. */
    @GetMapping(GET_MAP_VENUES)
    public ResponseEntity<List<VenueMapPinDto>> getMapVenues(@RequestParam Long cityId) {
        return ResponseEntity.ok(venuesCache.findByCityId(cityId));
    }

    @GetMapping(GET_VENUE_DETAIL)
    public ResponseEntity<VenueDetailDto> getVenueDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(venuesCache.findById(id));
    }

}
