package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.exceptions.AddressNotFoundException;
import com.kryptosystems.ballastasera.exceptions.DuplicateVenueException;
import com.kryptosystems.ballastasera.exceptions.VenueHasActiveEventsException;
import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
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
    private final EventsRepository eventsRepository;
    private final GeocodingService geocodingService;
    private final VenuesMapper venuesMapper;
    private final UsersService usersService;

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
        return buildAndSaveVenue(organizer, organizer.getUser(), dto);
    }

    @Override
    public Venues createAsAdmin(UUID adminUserId, VenueCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        Users createdBy = organizer.getUser() != null ? organizer.getUser() : usersService.findById(adminUserId);
        return buildAndSaveVenue(organizer, createdBy, dto);
    }

    private Venues buildAndSaveVenue(Organizers organizer, Users user, VenueCreateDto dto) {
        Cities city = citiesRepository.findById(dto.getCityId())
                .orElseThrow(() -> new EntityNotFoundException("City not found with id " + dto.getCityId()));

        venuesRepository.findByCityIdAndNameIgnoreCase(dto.getCityId(), dto.getName())
                .ifPresent(existing -> {
                    throw new DuplicateVenueException("A venue with name " + dto.getName() + " already exists in this city", existing.getId());
                });

        Venues venue = venuesMapper.toVenueEntity(dto);
        venue.setOrganizer(organizer);
        venue.setCreatedBy(user);
        venue.setCity(city);

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
    public Venues update(UUID id, UUID requesterId, VenueUpdateDto dto) {
        Venues venue = findById(id);
        assertOwnership(venue, requesterId);
        if (dto.getName() != null) {
            venuesRepository.findByCityIdAndNameIgnoreCaseAndIdNot(venue.getCity().getId(), dto.getName(), id)
                    .ifPresent(existing -> {
                        throw new DuplicateVenueException("A venue with name " + dto.getName() + " already exists in this city", existing.getId());
                    });
        }
        venuesMapper.updateVenueEntityFromDto(dto, venue);
        boolean addressChanged = dto.getAddress() != null && !dto.getAddress().equals(venue.getAddress());
        boolean coordsProvidedByClient = dto.getLatitude() != null && dto.getLongitude() != null;
        if (addressChanged && !coordsProvidedByClient) {
            GeocodingService.GeoPoint point = geocodingService.geoCode(dto.getAddress(), venue.getCity().getName())
                    .orElseThrow(() -> new AddressNotFoundException(
                            "Address not found: " + dto.getAddress() + ". Correct the address or insert the coordinates manually."));
            venue.setLatitude(point.latitude());
            venue.setLongitude(point.longitude());
        }
        return venuesRepository.save(venue);
    }

    @Override
    public void delete(UUID id) {
        Venues venue = findById(id);
        if (eventsRepository.existsByVenueIdAndStatusNot(id, EventStatus.CANCELLED)) {
            throw new VenueHasActiveEventsException("Venue " + id + " has active events and cannot be deleted");
        }
        venuesRepository.delete(venue);
    }

    @Override
    public List<Venues> search(Long cityId, String query) {
        if (query == null || query.isBlank()) {
            return venuesRepository.findByCityId(cityId);
        }
        return venuesRepository.findByCityIdAndNameContainingIgnoreCase(cityId, query);
    }

    private void assertOwnership(Venues venue, UUID requesterId) {
        if (venue.getOrganizer() == null || venue.getOrganizer().getUser() == null
                || !venue.getOrganizer().getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this venue");
        }
    }
}
