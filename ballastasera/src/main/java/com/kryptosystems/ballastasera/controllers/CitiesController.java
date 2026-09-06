package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.exceptions.InvalidPaginationException;
import com.kryptosystems.ballastasera.exceptions.InvalidEventTimingException;
import com.kryptosystems.ballastasera.models.dtos.CityDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.mappers.CitiesMapper;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.time.OffsetDateTime;

import static com.kryptosystems.ballastasera.utilities.RestConstants.CITIES;

@RestController
@RequestMapping(CITIES)
@RequiredArgsConstructor
public class CitiesController {

    private static final String GET_CITIES = "";
    private static final String GET_CITY = "/{id}";
    private static final String GET_CITY_EVENTS = "/{slug}/events";

    private final CitiesService citiesService;
    private final CitiesMapper citiesMapper;
    private final EventsService eventsService;

    @GetMapping(GET_CITIES)
    public ResponseEntity<List<CityDto>> getCities() {
        List<CityDto> cities = citiesService.findActive()
                .stream()
                .map(citiesMapper::toDto)
                .toList();

        return ResponseEntity.ok(cities);
    }

    @GetMapping(GET_CITY)
    public ResponseEntity<CityDto> getCity(@PathVariable Long id) {
        CityDto city = citiesMapper.toDto(citiesService.findActiveById(id));

        return ResponseEntity.ok(city);
    }

    @GetMapping(GET_CITY_EVENTS)
    public ResponseEntity<Page<EventCardDto>> getCityEvents(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "from", required = false)
            OffsetDateTime fromTime,
            @RequestParam(name = "to", required = false)
            OffsetDateTime toTime,
            @RequestParam(name = "danceStyle", required = false)
            String danceStyle
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPaginationException(
                    "page must be >= 0 and size must be between 1 and 100"
            );
        }

        if (fromTime != null
                && toTime != null
                && fromTime.isAfter(toTime)) {
            throw new InvalidEventTimingException(
                    "from must be before or equal to to"
            );
        }

        List<String> danceStyleSlugs =
                parseDanceStyleSlugs(danceStyle);

        Cities city = citiesService.findActiveBySlug(slug);
        return ResponseEntity.ok(
                eventsService.findPublicByCity(
                        city.getId(),
                        fromTime,
                        toTime,
                        danceStyleSlugs,
                        PageRequest.of(page, size)
                )
        );
    }

    private List<String> parseDanceStyleSlugs(String danceStyle) {
        if (danceStyle == null || danceStyle.isBlank()) {
            return List.of();
        }

        return Arrays.stream(danceStyle.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
