package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.exceptions.AddressNotFoundException;
import com.kryptosystems.ballastasera.exceptions.DuplicateVenueException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
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
import com.kryptosystems.ballastasera.services.manager.*;
import com.kryptosystems.ballastasera.utilities.ImageTypeValidator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VenuesServiceImpl implements VenuesService {

    private static final int LOGO_MAX_LONG_EDGE = 512;

    private final VenuesRepository venuesRepository;
    private final OrganizersRepository organizersRepository;
    private final CitiesRepository citiesRepository;
    private final EventsRepository eventsRepository;
    private final GeocodingService geocodingService;
    private final VenuesMapper venuesMapper;
    private final UsersService usersService;
    private final ObjectStorageService objectStorageService;
    private final WebpConverterService webpConverterService;

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
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.VENUE_BY_CITY,
            RedisCacheConfig.VENUE_MAP_BY_CITY}, allEntries = true)
    public Venues createVenueAsAdmin(UUID adminUserId, VenueCreateDto dto) {
        Organizers organizer = null;
        if (dto.getOrganizerId() != null) {
            organizer = organizersRepository.findById(dto.getOrganizerId())
                    .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        }
        return buildAndSaveVenue(organizer, usersService.findById(adminUserId), dto);
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
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.VENUE_BY_CITY,
            RedisCacheConfig.VENUE_MAP_BY_CITY, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public Venues updateVenueAsAdmin(UUID id, VenueUpdateDto dto) {
        Venues venue = findById(id);
        if (dto.getName() != null) {
            venuesRepository.findByCityIdAndNameIgnoreCaseAndIdNot(venue.getCity().getId(), dto.getName(), id)
                    .ifPresent(existing -> {
                        throw new DuplicateVenueException("A venue with name " + dto.getName() + " already exists in this city", existing.getId());
                    });
        }
        // Antes del mapper: despues venue.getAddress() ya tiene la direccion nueva y nunca detectaria el cambio.
        boolean addressChanged = dto.getAddress() != null && !dto.getAddress().equals(venue.getAddress());
        venuesMapper.updateVenueEntityFromDto(dto, venue);
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
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.VENUE_BY_CITY,
            RedisCacheConfig.VENUE_MAP_BY_CITY, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public Venues updateLogoAsAdmin(UUID id, MultipartFile file) {
        Venues venue = findById(id);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new MediaStorageException("Failed to read logo file", e);
        }
        ImageTypeValidator.detectExtension(content);
        byte[] webpContent = webpConverterService.convertToWebp(content, LOGO_MAX_LONG_EDGE);
        String previousLogoUrl = venue.getLogoUrl();
        venue.setLogoUrl(objectStorageService.uploadVenueLogo(id, webpContent));
        Venues savedVenue = venuesRepository.save(venue);
        deleteLogoQuietly(previousLogoUrl);
        return savedVenue;
    }

    @Override
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.VENUE_BY_CITY,
            RedisCacheConfig.VENUE_MAP_BY_CITY, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public Venues deleteLogoAsAdmin(UUID id) {
        Venues venue = findById(id);
        String previousLogoUrl = venue.getLogoUrl();
        venue.setLogoUrl(null);
        Venues savedVenue = venuesRepository.save(venue);
        deleteLogoQuietly(previousLogoUrl);
        return savedVenue;
    }

    @Override
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.VENUE_BY_CITY,
            RedisCacheConfig.VENUE_MAP_BY_CITY, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public void deleteVenue(UUID id) {
        Venues venue = findById(id);
        if (eventsRepository.existsByVenueIdAndStatusNot(id, EventStatus.CANCELLED)) {
            throw new VenueHasActiveEventsException("Venue " + id + " has active events and cannot be deleted");
        }
        venuesRepository.delete(venue);
        deleteLogoQuietly(venue.getLogoUrl());
    }

    @Override
    public List<Venues> search(Long cityId, String query) {
        if (query == null || query.isBlank()) {
            return venuesRepository.findByCityId(cityId);
        }
        return venuesRepository.findByCityIdAndNameContainingIgnoreCase(cityId, query);
    }

    private void deleteLogoQuietly(String logoUrl) {
        if (logoUrl == null) {
            return;
        }
        try {
            objectStorageService.deleteVenueLogo(logoUrl);
        } catch (MediaStorageException e) {
            log.warn("Failed to delete venue logo: {}", logoUrl, e);
        }
    }
}
