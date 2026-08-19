package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.Venues;

import java.util.Set;
import java.util.UUID;

/**
 * Resolucion de entidades relacionadas compartida entre Events y EventSeries:
 * ambos tienen la misma forma (city obligatoria, venue opcional que debe
 * pertenecer a esa city, danceStyles, coordenadas geocodificadas si faltan),
 * asi que esas reglas viven en un solo lugar en vez de repetidas en cada
 * ServiceImpl.
 */
public interface EventResolverService {

    Cities resolveCity(Long cityId);

    /** null si venueId es null. Lanza VenueCityMismatchException si el venue
     * pertenece a una ciudad distinta de cityId. */
    Venues resolveVenue(UUID venueId, Long cityId);

    /** Set vacio (nunca null) si danceStyleIds es null o vacio. */
    Set<DanceStyles> resolveDanceStyles(Set<Long> danceStyleIds);

    /** Geocodifica address+cityName. Lanza AddressNotFoundException si no se
     * encuentra ningun resultado. */
    GeocodingService.GeoPoint resolveCoordinates(String address, String cityName);
}
