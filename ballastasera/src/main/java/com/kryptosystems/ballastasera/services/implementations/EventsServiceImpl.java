package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventsServiceImpl implements EventsService {

    private final EventsRepository eventsRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsMapper eventsMapper;

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
                    EventCardDto dto = eventsMapper.toCardDto(event);
                    dto.setLiveNow(isLiveNow(event, now));
                    dto.setGoingCount(goingCounts.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .toList();
    }

    @Override
    public EventDetailDto getEventDetail(UUID id) {
        Events event = eventsRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id " + id));

        EventDetailDto dto = eventsMapper.toDetailDto(event);
        dto.setLiveNow(isLiveNow(event, OffsetDateTime.now()));
        dto.setGoingCount(eventAttendanceRepository.countByEventIdAndStatus(id, AttendanceStatus.GOING));
        dto.setInterestedCount(eventAttendanceRepository.countByEventIdAndStatus(id, AttendanceStatus.INTERESTED));
        dto.setInstagramUrl(event.getInstagramUrl() != null
                ? event.getInstagramUrl()
                : event.getOrganizer().getInstagram());
        return dto;
    }

    private boolean isLiveNow(Events event, OffsetDateTime now) {
        OffsetDateTime effectiveEnd = event.getEndAt() != null
                ? event.getEndAt()
                : event.getStartAt().plusHours(4);
        return !now.isBefore(event.getStartAt()) && now.isBefore(effectiveEnd);
    }
}