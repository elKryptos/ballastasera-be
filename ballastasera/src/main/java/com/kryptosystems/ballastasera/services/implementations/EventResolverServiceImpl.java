package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.AddressNotFoundException;
import com.kryptosystems.ballastasera.exceptions.VenueCityMismatchException;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.repositories.DanceStylesRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventResolverServiceImpl implements EventResolverService {

    private final CitiesRepository citiesRepository;
    private final VenuesRepository venuesRepository;
    private final DanceStylesRepository danceStylesRepository;
    private final GeocodingService geocodingService;

    @Override
    public Cities resolveCity(Long cityId) {
        return citiesRepository.findById(cityId)
                .orElseThrow(() -> new EntityNotFoundException("City not found with id " + cityId));
    }

    @Override
    public Venues resolveVenue(UUID venueId, Long cityId) {
        if (venueId == null) return null;
        Venues venue = venuesRepository.findById(venueId)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found with id " + venueId));

        if (!venue.getCity().getId().equals(cityId)) {
            throw new VenueCityMismatchException("Venue " + venueId + " belongs to a different city than the selected one");
        }
        return venue;
    }

    @Override
    public Set<DanceStyles> resolveDanceStyles(Set<Long> danceStyleIds) {
        if (danceStyleIds == null || danceStyleIds.isEmpty()) return new HashSet<>();
        return new HashSet<>(danceStylesRepository.findAllById(danceStyleIds));
    }

    @Override
    public GeocodingService.GeoPoint resolveCoordinates(String address, String cityName) {
        return geocodingService.geoCode(address, cityName)
                .orElseThrow(() -> new AddressNotFoundException(
                        "Address not found: " + address + ". Correct the address or insert the coordinates manually."));
    }
}
