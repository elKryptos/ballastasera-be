package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.exceptions.InvalidEventTimingException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.*;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.repositories.*;
import com.kryptosystems.ballastasera.services.manager.*;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private final EventsRepository eventsRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsMapper eventsMapper;
    private final OrganizersRepository organizersRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final EventResolverService eventResolverService;
    private final ObjectStorageService objectStorageService;
    private final EventFlyerProcessingService eventFlyerProcessingService;

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
    public List<EventCardDto> findMapEvents(double minLat, double maxLat, double minLng, double maxLng, Long cityId) {
        List<UUID> ids = eventsRepository.findActiveOrUpcomingIdsInBounds(minLat, maxLat, minLng, maxLng, cityId);
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, Events> eventsById = eventsRepository.findAllWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(Events::getId, e -> e));

        Map<UUID, Long> goingCounts = eventAttendanceRepository.countByEventIdInAndStatus(ids, AttendanceStatus.GOING).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        OffsetDateTime now = OffsetDateTime.now();

        return ids.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .map(event -> {
                    EventCardDto dto = eventsMapper.toEventCardDto(event);
                    dto.setLiveNow(EventTimingUtils.isLiveNow(event, now));
                    dto.setGoingCount(goingCounts.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .toList();
    }

    @Override
    public EventDetailDto getEventDetail(UUID id) {
        Events event = eventsRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id " + id));
        return buildDetailDto(event);
    }

    @Override
    public EventDetailDto toEventDetailDto(Events event) {
        return buildDetailDto(event);
    }

    /** Mapea + completa los campos que el mapper ignora a propósito (liveNow,
     * counts, fallback de instagramUrl). Recibe el Events ya cargado para no
     * forzar un roundtrip extra a la DB cuando el caller ya lo tiene en memoria. */
    private EventDetailDto buildDetailDto(Events event) {
        EventDetailDto dto = eventsMapper.toEventDetailDto(event);
        dto.setLiveNow(EventTimingUtils.isLiveNow(event, OffsetDateTime.now()));
        dto.setGoingCount(eventAttendanceRepository.countByEventIdAndStatus(event.getId(), AttendanceStatus.GOING));
        dto.setInterestedCount(eventAttendanceRepository.countByEventIdAndStatus(event.getId(), AttendanceStatus.INTERESTED));
        dto.setInstagramUrl(event.getInstagramUrl() != null
                ? event.getInstagramUrl()
                : event.getOrganizer().getInstagram());
        return dto;
    }

    @Override
    public Events create(UUID requesterId, EventCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        if (organizer.getUser() == null || !organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this organizer");
        }
        if (!organizer.isVerified()) {
            throw new AccessDeniedException("Organizer not verified yet");
        }
        return buildAndSaveEvent(organizer, dto);
    }

    @Override
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
        event.setStatus(EventStatus.PENDING);
        /** Si el cliente no mando lat/lng (ej. no arrastro el pin en el mapa),
         * las calculamos a partir de la dirección. */
        if (event.getLatitude() == null || event.getLongitude() == null) {
            GeocodingService.GeoPoint point = eventResolverService.resolveCoordinates(event.getAddress(), event.getCity().getName());
            event.setLatitude(point.latitude());
            event.setLongitude(point.longitude());
        }
        return eventsRepository.save(event);
    }

    @Override
    public Events update(UUID id, UUID requesterId, EventUpdateDto dto) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        Organizers organizer = event.getOrganizer();
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
            event.setSeries(resolveSeries(dto.getSeriesId(), organizer));
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
        /** Solo re-geocodificamos si cambio la direccion y el cliente no mando
         * coordenadas explicitas (ej. ajusto el pin a mano en el mapa). */
        boolean addressChanged = dto.getAddress() != null;
        boolean coordsProvidedByClient = dto.getLatitude() != null && dto.getLongitude() != null;
        if (addressChanged && !coordsProvidedByClient) {
            GeocodingService.GeoPoint point = eventResolverService.resolveCoordinates(dto.getAddress(), event.getCity().getName());
            event.setLatitude(point.latitude());
            event.setLongitude(point.longitude());
        }
        return eventsRepository.save(event);
    }

    @Override
    public Events updateStatus(UUID requesterId, UUID id, EventStatus status) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        event.setStatus(status);
        return eventsRepository.save(event);
    }

    @Override
    public Events updateFlyer(UUID id, UUID requesterId, MultipartFile file) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        return applyFlyer(event, file);
    }

    @Override
    public Events updateFlyerAsAdmin(UUID id, MultipartFile file) {
        return applyFlyer(findById(id), file);
    }

    @Override
    public Events deleteFlyer(UUID id, UUID requesterId) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        return removeFlyer(event);
    }

    @Override
    public Events deleteFlyerAsAdmin(UUID id) {
        return removeFlyer(findById(id));
    }

    private Events removeFlyer(Events event) {
        objectStorageService.deleteEventFlyerRaw(event.getId());
        objectStorageService.deleteEventFlyerFinal(event.getId());
        event.setFlyerUrl(null);
        event.setFlyerStatus(FlyerStatus.NONE);
        return eventsRepository.save(event);
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

    @Override
    public void delete(UUID id, UUID requesterId) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        objectStorageService.deleteEventFlyerRaw(id);
        objectStorageService.deleteEventFlyerFinal(id);
        eventsRepository.delete(event);
    }

    @Override
    public Events removeVenue(UUID eventId, UUID requesterId) {
        Events event = findById(eventId);
        assertOwnership(event, requesterId);
        event.setVenue(null);
        return eventsRepository.save(event);
    }

    @Override
    public List<Events>  findPublishedByOrganizerId(UUID organizerId) {
        return eventsRepository.findByOrganizerIdAndStatusOrderByStartAtDesc(
                organizerId,
                EventStatus.PUBLISHED
        );
    }

    private void assertOwnership(Events event, UUID requesterId) {
        if (event.getOrganizer().getUser() == null || !event.getOrganizer().getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
    }

    private EventSeries resolveSeries(UUID seriesId, Organizers organizer) {
        if (seriesId == null) return null;
        EventSeries series = eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new EntityNotFoundException("Event series not found with id " + seriesId));
        if (!series.getOrganizer().getId().equals(organizer.getId())) {
            throw new AccessDeniedException("Event series does not belong to this organizer");
        }
        return series;
    }

    @Override
    public Page<EventCardDto> findPublicByCity(Long cityId, Pageable pageable) {
        OffsetDateTime now = OffsetDateTime.now();

        Page<UUID> idPage = eventsRepository.findPublicEventIdsByCity(cityId, now, pageable);
        List<UUID> ids = idPage.getContent();

        if(ids.isEmpty()){
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        Map<UUID, Events> eventsById = eventsRepository
                .findAllWithDetailsByIdIn(ids)
                .stream().collect(Collectors.toMap(Events::getId, event -> event));

        Map<UUID, Long> goingCounts = eventAttendanceRepository
                .countByEventIdInAndStatus(ids, AttendanceStatus.GOING)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));

        List<EventCardDto> cards = ids.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .map(event -> {
                    EventCardDto dto = eventsMapper.toEventCardDto(event);
                    dto.setLiveNow(EventTimingUtils.isLiveNow(event, now));
                    dto.setGoingCount(
                            goingCounts.getOrDefault(event.getId(), 0L)
                    );
                    return dto;
                })
                .toList();

        return new PageImpl<>(
                cards,
                idPage.getPageable(),
                idPage.getTotalElements()
        );

    }

}
