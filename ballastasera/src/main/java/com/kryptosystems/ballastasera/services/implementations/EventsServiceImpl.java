package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.*;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.repositories.*;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

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
    private final CitiesRepository citiesRepository;
    private final VenuesRepository venuesRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final DanceStylesRepository danceStylesRepository;

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

    /** Mapea + completa los campos que el mapper ignora a proposito (liveNow,
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
        if (!organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this organizer");
        }
        if (!organizer.isVerified()) {
            throw new AccessDeniedException("Organizer not verified yet");
        }
        Events event = eventsMapper.toEventEntity(dto);
        event.setOrganizer(organizer);
        event.setCity(resolveCity(dto.getCityId()));
        event.setVenue(resolveVenue(dto.getVenueId()));
        event.setSeries(resolveSeries(dto.getSeriesId()));
        event.setDanceStyles(resolveDanceStyles(dto.getDanceStyleIds()));
        event.setSlug(SlugUtils.uniqueSlug(dto.getTitle(),
                slug -> eventsRepository.findBySlug(slug).isPresent()));
        event.setStatus(EventStatus.PENDING);
        return eventsRepository.save(event);
    }

    @Override
    public Events update(UUID id, UUID requesterId, EventUpdateDto dto) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        eventsMapper.updateEventEntityFromDto(dto, event);
        if (dto.getCityId() != null) {
           event.setCity(resolveCity(dto.getCityId()));
        }
        if (dto.getVenueId() != null) {
            event.setVenue(resolveVenue(dto.getVenueId()));
        }
        if (dto.getSeriesId() != null) {
            event.setSeries(resolveSeries(dto.getSeriesId()));
        }
        if (dto.getDanceStyleIds() != null) {
            event.setDanceStyles(resolveDanceStyles(dto.getDanceStyleIds()));
        }
        if (dto.getTitle() != null) {
            event.setSlug(SlugUtils.uniqueSlug(dto.getTitle(),
                    slug -> eventsRepository.findBySlug(slug)
                            .map(existing -> !existing.getId().equals(id))
                            .orElse(false)));
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
    public void delete(UUID id, UUID requesterId) {
        Events event = findById(id);
        assertOwnership(event, requesterId);
        eventsRepository.delete(event);
    }

    private void assertOwnership(Events event, UUID requesterId) {
        if (!event.getOrganizer().getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
    }

    private Cities resolveCity(Long cityId) {
        return citiesRepository.findById(cityId)
                .orElseThrow(() -> new EntityNotFoundException("City not found with id " + cityId));
    }

    private Venues resolveVenue(UUID venueId) {
        if (venueId == null) return null;
        return venuesRepository.findById(venueId)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found with id " + venueId));
    }

    private EventSeries resolveSeries(UUID seriesId) {
        if (seriesId == null) return null;
        return eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new EntityNotFoundException("Event series not found with id " + seriesId));
    }

    private Set<DanceStyles> resolveDanceStyles(Set<Long> danceStyleIds) {
        if (danceStyleIds == null || danceStyleIds.isEmpty()) return null;
        return new HashSet<>(danceStylesRepository.findAllById(danceStyleIds));
    }

}