package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.AddressNotFoundException;
import com.kryptosystems.ballastasera.exceptions.DuplicateVenueException;
import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenuesServiceImpl implements VenuesService {

    private final VenuesRepository venuesRepository;
    private final OrganizersRepository organizersRepository;
    private final CitiesRepository citiesRepository;
    private final GeocodingService geocodingService;

    @Override
    public List<Venues> findAll() {
        return venuesRepository.findAll();
    }

    @Override
    public Venues findById(UUID id) {
        return venuesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found with id " + id));
    }

    @Override
    public List<Venues> findByCityId(Long cityId) {
        return venuesRepository.findByCityId(cityId);
    }

    @Override
    public List<Venues> findByOrganizerId(UUID organizerId) {
        return venuesRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Venues save(Venues venue) {
        return venuesRepository.save(venue);
    }

    @Override
    public void deleteById(UUID id) {
        venuesRepository.deleteById(id);
    }

    @Override
    public Venues create(UUID requesterId, VenueCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        if (!organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this organizer");
        }
        if (!organizer.isVerified()) {
            throw new AccessDeniedException("Organizer not verified yet");
        }
        Cities city = citiesRepository.findById(dto.getCityId())
                .orElseThrow(() -> new EntityNotFoundException("City not found with id " + dto.getCityId()));

        venuesRepository.findByCityIdAndNameIgnoreCase(dto.getCityId(), dto.getName())
                .ifPresent(existing -> {
                    throw new DuplicateVenueException("A venue with name " + dto.getName() + " already exists in this city", existing.getId());
                });

        Venues venue = new Venues();
        venue.setOrganizer(organizer);
        venue.setCreatedBy(organizer.getUser());
        venue.setCity(city);
        venue.setName(dto.getName());
        venue.setAddress(dto.getAddress());
        venue.setPostalCode(dto.getPostalCode());
        venue.setDescription(dto.getDescription());
        venue.setLatitude(dto.getLatitude());
        venue.setLongitude(dto.getLongitude());

        if (venue.getLatitude() == null || venue.getLongitude() == null) {
            GeocodingService.GeoPoint point = geocodingService.geoCode(venue.getAddress(), city.getName())
                    .orElseThrow(() ->  new AddressNotFoundException(
                            "Address not found: " + venue.getAddress() + ". Correct the address or insert the coordinates manually."));
            venue.setLatitude(point.latitude());
            venue.setLongitude(point.longitude());
        }

        return venuesRepository.save(venue);
    }

    @Override
    public List<Venues> search(Long cityId, String query) {
        if (query == null || query.isBlank()) {
            return venuesRepository.findByCityId(cityId);
        }
        return venuesRepository.findByCityIdAndNameContainingIgnoreCase(cityId, query);
    }
}
