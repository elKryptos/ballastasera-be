package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.exceptions.EventSeriesInactiveException;
import com.kryptosystems.ballastasera.exceptions.InvalidEventTimingException;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesUpdateDto;
import com.kryptosystems.ballastasera.models.entities.*;
import com.kryptosystems.ballastasera.models.mappers.EventSeriesMapper;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventSeriesServiceImpl implements EventSeriesService {

    private final EventSeriesRepository eventSeriesRepository;
    private final EventSeriesMapper eventSeriesMapper;
    private final OrganizersRepository organizersRepository;
    private final EventsRepository eventsRepository;
    private final EventResolverService eventResolverService;

    @Override
    public List<EventSeries> findAll() {
        return eventSeriesRepository.findAll();
    }

    @Override
    public EventSeries findById(UUID id) {
        return eventSeriesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event series not found with id " + id));
    }

    @Override
    public List<EventSeries> findByOrganizerId(UUID organizerId) {
        return eventSeriesRepository.findByOrganizerId(organizerId);
    }

    @Override
    public List<EventSeries> findByVenueId(UUID venueId) {
        return eventSeriesRepository.findByVenueId(venueId);
    }

    @Override
    public List<EventSeries> findByCityId(Long cityId) {
        return eventSeriesRepository.findByCityId(cityId);
    }

    @Override
    public EventSeriesDetailDto getEventSeriesDetail(UUID id) {
        EventSeries series = eventSeriesRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Event series not found with id " + id));
        return buildDetailDto(series);
    }

    @Override
    public EventSeriesDetailDto toEventSeriesDetailDto(EventSeries series) {
        return buildDetailDto(series);
    }

    private EventSeriesDetailDto buildDetailDto(EventSeries series) {
        EventSeriesDetailDto dto = eventSeriesMapper.toEventSeriesDetailDto(series);
        dto.setInstagramUrl(series.getInstagramUrl() != null
                ? series.getInstagramUrl()
                : series.getOrganizer().getInstagram());
        return dto;
    }

    @Override
    public EventSeries create(UUID requesterId, EventSeriesCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        if (organizer.getUser() == null || !organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this organizer");
        }
        if (!organizer.isVerified()) {
            throw new AccessDeniedException("Organizer not verified yet");
        }
        return buildAndSaveSeries(organizer, dto);
    }

    @Override
    public EventSeries createAsAdmin(EventSeriesCreateDto dto) {
        Organizers organizer = organizersRepository.findById(dto.getOrganizerId())
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + dto.getOrganizerId()));
        return buildAndSaveSeries(organizer, dto);
    }

    private EventSeries buildAndSaveSeries(Organizers organizer, EventSeriesCreateDto dto) {
        EventSeries series = eventSeriesMapper.toEventSeriesEntity(dto);
        series.setOrganizer(organizer);
        series.setCity(eventResolverService.resolveCity(dto.getCityId()));
        series.setVenue(eventResolverService.resolveVenue(dto.getVenueId(), series.getCity().getId()));
        series.setDanceStyles(eventResolverService.resolveDanceStyles(dto.getDanceStyleIds()));
        if (series.getLatitude() == null || series.getLongitude() == null) {
            GeocodingService.GeoPoint point = eventResolverService.resolveCoordinates(series.getAddress(), series.getCity().getName());
            series.setLatitude(point.latitude());
            series.setLongitude(point.longitude());
        }
        return eventSeriesRepository.save(series);
    }

    @Override
    public EventSeries update(UUID id, UUID requesterId, EventSeriesUpdateDto eventSeriesUpdateDto) {
        EventSeries series = findById(id);
        assertOwnership(series, requesterId);
        eventSeriesMapper.updateEventSeriesEntityFromDto(eventSeriesUpdateDto, series);
        if (eventSeriesUpdateDto.getCityId() != null) {
            series.setCity(eventResolverService.resolveCity(eventSeriesUpdateDto.getCityId()));
        }
        if (eventSeriesUpdateDto.getVenueId() != null) {
            series.setVenue(eventResolverService.resolveVenue(eventSeriesUpdateDto.getVenueId(), series.getCity().getId()));
        }
        if (eventSeriesUpdateDto.getDanceStyleIds() != null) {
            series.setDanceStyles(eventResolverService.resolveDanceStyles(eventSeriesUpdateDto.getDanceStyleIds()));
        }
        boolean addressChanged = eventSeriesUpdateDto.getAddress() != null && !eventSeriesUpdateDto.getAddress().equals(series.getAddress());
        boolean coordsProvidedByClient = eventSeriesUpdateDto.getLatitude() != null && eventSeriesUpdateDto.getLongitude() != null;
        if (addressChanged && !coordsProvidedByClient) {
            GeocodingService.GeoPoint point = eventResolverService.resolveCoordinates(eventSeriesUpdateDto.getAddress(), series.getCity().getName());
            series.setLatitude(point.latitude());
            series.setLongitude(point.longitude());
        }
        return eventSeriesRepository.save(series);
    }

    @Override
    public void delete(UUID id, UUID requesterId) {
        EventSeries series = findById(id);
        assertOwnership(series, requesterId);
        eventSeriesRepository.delete(series);
    }

    @Override
    public EventSeries removeVenue(UUID seriesId, UUID requesterId) {
        EventSeries series = findById(seriesId);
        assertOwnership(series, requesterId);
        series.setVenue(null);
        return eventSeriesRepository.save(series);
    }

    @Override
    public List<Events> generateOccurences(UUID seriesId, UUID requesterId, LocalDate startDate, LocalDate endDate) {
        EventSeries series = findById(seriesId);
        assertOwnership(series, requesterId);
        return doGenerateOccurrences(series, startDate, endDate);
    }

    @Override
    public List<Events> generateOccurencesAsAdmin(UUID seriesId, LocalDate startDate, LocalDate endDate) {
        EventSeries series = findById(seriesId);
        return doGenerateOccurrences(series, startDate, endDate);
    }

    /** Genera un Events por cada fecha en [from, until] cuyo día de semana
     * esté en recurrenceDays, arrancando desde el día siguiente a
     * generatedUntil para no duplicar ocurrencias ya generadas. */
    private List<Events> doGenerateOccurrences(EventSeries series, LocalDate startDate, LocalDate endDate) {
        if (!series.isActive()) {
            throw new EventSeriesInactiveException("Event series " + series.getId() + " in not active");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidEventTimingException("until (" + endDate + ") must not be before from (" + startDate + ")");
        }
        LocalDate effectiveStartDate = series.getGeneratedUntil() != null && series.getGeneratedUntil().plusDays(1).isAfter(startDate)
                ? series.getGeneratedUntil().plusDays(1)
                : startDate;
        List<Events> occurrences = new ArrayList<>();
        for (LocalDate date = effectiveStartDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (series.getRecurrenceDays().contains(date.getDayOfWeek())) {
                occurrences.add(buildOccurrence(series, date));
            }
        }

        List<Events> saved = occurrences.isEmpty() ? List.of() : eventsRepository.saveAll(occurrences);
        series.setGeneratedUntil(endDate);
        eventSeriesRepository.save(series);
        return saved;
    }

    /** Mapper manual MapStruct no hace mapping entre entidades*/
    private Events buildOccurrence(EventSeries series, LocalDate date) {
        Events event = new Events();
        event.setOrganizer(series.getOrganizer());
        event.setVenue(series.getVenue());
        event.setSeries(series);
        event.setCity(series.getCity());
        event.setTitle(series.getTitle());
        event.setDescription(series.getDescription());
        event.setFlyerUrl(series.getFlyerUrl());
        event.setInstagramUrl(series.getInstagramUrl());
        event.setWhatsappUrl(series.getWhatsappUrl());
        event.setFree(series.isFree());
        event.setPrice(series.getPrice());
        event.setCurrency(series.getCurrency());
        event.setAddress(series.getAddress());
        event.setLatitude(series.getLatitude());
        event.setLongitude(series.getLongitude());
        event.setDanceStyles(new HashSet<>(series.getDanceStyles()));
        event.setStartAt(date.atTime(series.getStartTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        if (series.getEndTime() != null) {
            /** Si endTime no es posterior a startTime (ej. empieza 21:30 y
             * termina 00:00), la fiesta termina al dia siguiente: sin esto
             * end_at quedaria antes que start_at y violaria chk_event_time. */
            LocalDate endDate = series.getEndTime().isAfter(series.getStartTime()) ? date : date.plusDays(1);
            event.setEndAt(endDate.atTime(series.getEndTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        }
        event.setSlug(SlugUtils.uniqueSlug(series.getTitle() + "-" + date,
                slug -> eventsRepository.findBySlug(slug).isPresent()));
        /** Nace PUBLISHED: la serie ya pasó el mismo chequeo de ownership y
         * verificación de organizer que un evento suelto; pedir aprobación
         * manual clase por clase no tiene sentido para algo semanal. */
        event.setStatus(EventStatus.PUBLISHED);
        return event;
    }

    private void assertOwnership(EventSeries series, UUID requesterId) {
        if (series.getOrganizer().getUser() == null || !series.getOrganizer().getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event series");
        }
    }

}
