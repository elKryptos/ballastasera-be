package com.kryptosystems.ballastasera.services.implementations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/** Geocoding via Photon (https://photon.komoot.io), con fallback a Nominatim
 * si Photon falla o no devuelve resultados. Ambos basados en datos OSM;
 * Nominatim exige un User-Agent identificable segun su ToS. */
@Slf4j
@Service
public class GeocodingServiceImpl implements GeocodingService {

    private final RestClient photonClient;
    private final RestClient nominatimClient;

    public GeocodingServiceImpl(
            @Value("${geocoding.photon.base-url}") String photonBaseUrl,
            @Value("${geocoding.nominatim.base-url}") String nominatimBaseUrl,
            @Value("${geocoding.nominatim.user-agent}") String nominatimUserAgent) {
        this.photonClient = RestClient.builder()
                .baseUrl(photonBaseUrl)
                .build();
        this.nominatimClient = RestClient.builder()
                .baseUrl(nominatimBaseUrl)
                .defaultHeader("User-Agent", nominatimUserAgent)
                .build();
    }

    @Override
    public Optional<GeoPoint> geoCode(String address, String cityName) {
        String query = cityName != null ? address + ", " + cityName + ", Italy" : address;

        Optional<GeoPoint> result = geoCodeWithPhoton(query);
        if (result.isPresent()) {
            return result;
        }
        log.warn("Photon no devolvio resultados para '{}', probando fallback con Nominatim", query);
        return geoCodeWithNominatim(query);
    }

    private Optional<GeoPoint> geoCodeWithPhoton(String query) {
        try {
            PhotonResponse response = photonClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api")
                            .queryParam("q", query)
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(PhotonResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                return Optional.empty();
            }

            List<Double> coordinates = response.features().get(0).geometry().coordinates();
            double longitude = coordinates.get(0);
            double latitude = coordinates.get(1);
            return Optional.of(new GeoPoint(latitude, longitude));
        } catch (RestClientException ex) {
            log.error("Error calling Photon API for the address search: {}", query, ex);
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> geoCodeWithNominatim(String query) {
        try {
            List<NominatimResult> results = nominatimClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() {});

            if (results == null || results.isEmpty()) {
                log.info("No results found for address {} (Nominatim fallback)", query);
                return Optional.empty();
            }

            NominatimResult first = results.get(0);
            return Optional.of(new GeoPoint(
                    Double.parseDouble(first.lat()),
                    Double.parseDouble(first.lon())));
        } catch (RestClientException ex) {
            log.error("Error calling Nominatim API (fallback) for the address search: {}", query, ex);
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonResponse(List<PhotonFeature> features) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonFeature(PhotonGeometry geometry) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonGeometry(List<Double> coordinates) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon) { }
}
