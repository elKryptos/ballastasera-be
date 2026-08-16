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

/** Geocoding via Nominatim (OpenStreetMap). Gratuito, sin API key, pero con
 * un ToS que exige un header User-Agent identificable y un limite real de
 * 1 request/segundo — para el volumen de creacion/edicion de eventos
 * esperado no hace falta throttling explicito del lado del cliente. */
@Slf4j
@Service
public class GeocodingServiceImpl implements GeocodingService {

    private final RestClient restClient;

    public GeocodingServiceImpl(
            @Value("${geocoding.nominatim.base-url}") String baseUrl,
            @Value("${geocoding.nominatim.user-agent}") String userAgent) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Override
    public Optional<GeoPoint> geoCode(String address, String cityName) {
        String query = cityName != null ? address + ", " + cityName + ", Italy" : address;
        try {
            List<NominatimResult> results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() {});

            if (results == null || results.isEmpty()) {
                log.info("No results found for address {}", address);
                return Optional.empty();
            }

            NominatimResult first = results.get(0);
            return Optional.of(new GeoPoint(
                    Double.parseDouble(first.lat),
                    Double.parseDouble(first.lon))
            );
        } catch (RestClientException ex) {
            log.error("Error calling Nominatim API for the address search: {}", query, ex);
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(String lat, String lon) {
    }

}
