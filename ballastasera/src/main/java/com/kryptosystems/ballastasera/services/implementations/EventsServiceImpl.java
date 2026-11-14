package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.EventManagementTab;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.exceptions.InvalidEventTimingException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.*;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.repositories.*;
import com.kryptosystems.ballastasera.services.manager.*;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private final EventsRepository eventsRepository;
    private final EventsMapper eventsMapper;
    private final OrganizersRepository organizersRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final EventResolverService eventResolverService;
    private final ObjectStorageService objectStorageService;
    private final EventFlyerProcessingService eventFlyerProcessingService;
    private final VenuesRepository venuesRepository;

    private static final Duration MAP_DURATION = Duration.ofDays(60);
    private static final int MAP_LIMIT = 150;

    @Override
    public List<Events> findAll() {
        return eventsRepository.findAll();
    }

    @Override
    public Events findById(UUID id) {
        return eventsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id " + id));
    }

    @Override
    public Events findBySlug(String slug) {
        return eventsRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with slug " + slug));
    }

    @Override
    public List<Events> findByOrganizerId(UUID organizerId) {
        return eventsRepository.findByOrganizerId(organizerId);
    }

    @Override
    public List<Events> findByVenueId(UUID venueId) {
        return eventsRepository.findByVenueId(venueId);
    }

    @Override
    public List<Events> findBySeriesId(UUID seriesId) {
        return eventsRepository.findBySeriesId(seriesId);
    }

    @Override
    public List<Events> findUpcomingPublishedByCity(Long cityId) {
        return eventsRepository.findByCityIdAndStatusAndStartAtGreaterThanEqualOrderByStartAtAsc(
                cityId, EventStatus.PUBLISHED, OffsetDateTime.now());
    }

    @Override
    public Events save(Events event) {
        return eventsRepository.save(event);
    }

    @Override
    public void deleteById(UUID id) {
        eventsRepository.deleteById(id);
    }

    @Override
    public Slice<Events> findMapEvents(double minLat, double maxLat, double minLng, double maxLng, Long cityId) {
        OffsetDateTime to = OffsetDateTime.now().plus(MAP_DURATION);
        Slice<UUID> ids = eventsRepository.findActiveOrUpcomingIdsInBounds(
                minLat, maxLat, minLng, maxLng, cityId, to, PageRequest.of(0, MAP_LIMIT));
        return new SliceImpl<>(loadInOrder(ids.getContent()), ids.getPageable(), ids.hasNext());
    }

    @Override
    public Events findByIdWithDetails(UUID id) {
        return eventsRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id " + id));
    }

    @Override
    @Transactional
    public Events create(UUID requesterId, EventCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        if (organizer.getUser() == null || !organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this organizer");
        }
        if (!organizer.isVerified()) {
            throw new AccessDeniedException("Organizer not verified yet");
        }
        assertVenueAllowed(organizer, dto.getVenueId());
        return buildAndSaveEvent(organizer, dto);
    }

    @Override
    @Transactional
    public Events createAsAdmin(EventCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        return buildAndSaveEvent(organizer, dto);
    }

    private Events buildAndSaveEvent(Organizers organizer, EventCreateDto dto) {
        Events event = eventsMapper.toEventEntity(dto);
        event.setOrganizer(organizer);
        event.setCity(eventResolverService.resolveCity(dto.getCityId()));
        event.setVenue(eventResolverService.resolveVenue(dto.getVenueId(), event.getCity().getId()));
        event.setSeries(resolveSeries(dto.getSeriesId(), organizer));
        event.setDanceStyles(eventResolverService.resolveDanceStyles(dto.getDanceStyleIds()));
        event.setSlug(SlugUtils.uniqueSlug(dto.getTitle(),
                slug -> eventsRepository.findBySlug(slug).isPresent()));
        event.setStatus(EventStatus.PUBLISHED);
        /** Si el cliente no mando lat/lng las calculamos a partir de la dirección.
         * Es un Fallback para recalcular las coordenadas ahorramos una llamada a la API.
         * Actualmente el FE deberia enviar todos los datos desde la api de Photon, es solo de seguridad*/
        if (event.getLatitude() == null || event.getLongitude() == null) {
            GeocodingService.GeoPoint point = eventResolverService.resolveCoordinates(event.getAddress(), event.getCity().getName());
            event.setLatitude(point.latitude());
            event.setLongitude(point.longitude());
        }
        return eventsRepository.save(event);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events update(UUID id, UUID requesterId, EventUpdateDto dto) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        // D12: solo si el body cambia el venue; antes de tocar la entidad
        if (dto.getVenueId() != null) {
            assertVenueAllowed(event.getOrganizer(), dto.getVenueId());
        }
        return applyUpdate(event, dto);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events updateAsAdmin(UUID id, EventUpdateDto dto) {
        return applyUpdate(findById(id), dto);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events updateStatus(UUID requesterId, UUID id, EventStatus status) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        event.setStatus(status);
        return eventsRepository.save(event);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events updateStatusAsAdmin(UUID id, EventStatus status) {
        Events event = findById(id);
        event.setStatus(status);
        return eventsRepository.save(event);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events updateFlyer(UUID id, UUID requesterId, MultipartFile file) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        return applyFlyer(event, file);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events updateFlyerAsAdmin(UUID id, MultipartFile file) {
        return applyFlyer(findById(id), file);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events deleteFlyer(UUID id, UUID requesterId) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        return removeFlyer(event);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public Events deleteFlyerAsAdmin(UUID id) {
        return removeFlyer(findById(id));
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public void delete(UUID id, UUID requesterId) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        deleteEvent(event);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public void deleteAsAdmin(UUID id) {
        deleteEvent(findById(id));
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#eventId")
    public Events removeVenue(UUID eventId, UUID requesterId) {
        Events event = findById(eventId);
        assertOwnership(event, requesterId);
        // D12: un CLUB/VENUE no puede dejar el evento sin su venue
        assertVenueAllowed(event.getOrganizer(), null);
        event.setVenue(null);
        return eventsRepository.save(event);
    }

    @Override
    public List<Events> findPublishedByOrganizerId(UUID organizerId) {
        return eventsRepository.findByOrganizerIdAndStatusOrderByStartAtDesc(
                organizerId,
                EventStatus.PUBLISHED
        );
    }

    @Override
    public Page<Events> findForManagement(EventManagementTab tab, UUID organizerId, String search, Pageable pageable) {
        return loadManagementPage(tab, organizerId, null, search, pageable);
    }

    @Override
    public Page<Events> findMineForManagement(UUID requesterId, EventManagementTab tab, Pageable pageable) {
        return loadManagementPage(tab, null, requesterId, null, pageable);
    }

    private Events applyFlyer(Events event, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new MediaStorageException("Could not read uploaded file", e);
        }
        objectStorageService.uploadEventFlyerRaw(event.getId(), content);
        event.setFlyerStatus(FlyerStatus.PROCESSING);
        Events savedEvent = eventsRepository.save(event);
        eventFlyerProcessingService.convertAndPublish(event.getId(), content);
        return savedEvent;
    }

    private Events removeFlyer(Events event) {
        event.setFlyerUrl(null);
        event.setFlyerStatus(FlyerStatus.NONE);
        Events savedEvent = eventsRepository.save(event);
        objectStorageService.deleteEventFlyerRaw(event.getId());
        objectStorageService.deleteEventFlyerFinal(event.getId());
        return savedEvent;
    }

    private void deleteEvent(Events event) {
        eventsRepository.delete(event);
        objectStorageService.deleteEventFlyerRaw(event.getId());
        objectStorageService.deleteEventFlyerFinal(event.getId());
    }

    /** D12: un CLUB o VENUE solo publica en sus propios venues (venueId obligatorio y suyo). Solo los métodos
     * del organizador; los AsAdmin no lo llaman. */
    private void assertVenueAllowed(Organizers organizer, UUID venueId) {
        if (!organizer.getType().isVenueBound()) {
            return;
        }
        if (venueId == null || !venuesRepository.existsByIdAndOrganizerId(venueId, organizer.getId())) {
            throw new AccessDeniedException("Venue not owned by this organizer");
        }
    }

    /** Si el organizer fue borrado, nadie puede reclamar ser el dueño vía esta implementación se deniega el update */
    private void assertOwnership(Events event, UUID requesterId) {
        Organizers organizer = event.getOrganizer();
        if (organizer == null || organizer.getUser() == null || !organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
    }

    private EventSeries resolveSeries(UUID seriesId, Organizers organizer) {
        if (seriesId == null) return null;
        EventSeries series = eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new EntityNotFoundException("Event series not found with id " + seriesId));
        UUID seriesOrganizerId = series.getOrganizer() != null ? series.getOrganizer().getId() : null;
        UUID eventOrganizerId = organizer != null ? organizer.getId() : null;
        if (!Objects.equals(seriesOrganizerId, eventOrganizerId)) {
            throw new AccessDeniedException("Event series does not belong to this organizer");
        }
        return series;
    }

    private Events applyUpdate(Events event, EventUpdateDto dto) {
        UUID id = event.getId();
        eventsMapper.updateEventEntityFromDto(dto, event);
        if (event.getEndAt() != null && !event.getStartAt().isBefore(event.getEndAt())) {
            throw new InvalidEventTimingException(
                    "endAt (" + event.getEndAt() + ") must be after startAt (" + event.getStartAt() + ")"
            );
        }
        if (dto.getCityId() != null) {
            event.setCity(eventResolverService.resolveCity(dto.getCityId()));
        }
        if (dto.getVenueId() != null) {
            event.setVenue(eventResolverService.resolveVenue(dto.getVenueId(), event.getCity().getId()));
        }
        if (dto.getSeriesId() != null) {
            event.setSeries(resolveSeries(dto.getSeriesId(), event.getOrganizer()));
        }
        if (dto.getDanceStyleIds() != null) {
            event.setDanceStyles(eventResolverService.resolveDanceStyles(dto.getDanceStyleIds()));
        }
        if (dto.getTitle() != null) {
            event.setSlug(SlugUtils.uniqueSlug(dto.getTitle(),
                    slug -> eventsRepository.findBySlug(slug)
                            .map(existing -> !existing.getId().equals(id))
                            .orElse(false)));
        }
        /** Solo re geo codificamos si cambio la dirección y el cliente no mando
         * coordenadas (ej. ajusto el pin a mano en el mapa). */
        boolean addressChanged = dto.getAddress() != null;
        boolean coordsProvidedByClient = dto.getLatitude() != null && dto.getLongitude() != null;
        if (addressChanged && !coordsProvidedByClient) {
            GeocodingService.GeoPoint point = eventResolverService.resolveCoordinates(dto.getAddress(), event.getCity().getName());
            event.setLatitude(point.latitude());
            event.setLongitude(point.longitude());
        }
        return eventsRepository.save(event);
    }

    private Page<Events> loadManagementPage(EventManagementTab tab, UUID organizerId, UUID ownerId,
                                            String search, Pageable pageable) {
        Sort.Direction direction = tab.isEnded() ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(direction, "startAt").and(Sort.by(direction, "id")));
        String titlePattern = (search == null || search.isBlank())
                ? "%"
                : "%" + search.trim().toLowerCase() + "%";
        Page<UUID> ids = eventsRepository.findIdsForManagement(tab.getStatuses(), tab.isEnded(), OffsetDateTime.now(),
                organizerId, ownerId, titlePattern, sorted);
        return new PageImpl<>(loadInOrder(ids.getContent()), ids.getPageable(), ids.getTotalElements());
    }

    /** Carga con detalles un lote de ids respetando su orden (findAllWithDetailsByIdIn no lo garantiza). */
    private List<Events> loadInOrder(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        Map<UUID, Events> eventsById = eventsRepository.findAllWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(Events::getId, e -> e));
        return ids.stream().map(eventsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

}
