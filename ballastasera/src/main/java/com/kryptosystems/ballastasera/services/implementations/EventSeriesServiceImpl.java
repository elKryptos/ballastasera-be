package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.dtos.EventSeriesCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesUpdateDto;
import com.kryptosystems.ballastasera.models.entities.*;
import com.kryptosystems.ballastasera.models.mappers.EventSeriesMapper;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventSeriesServiceImpl implements EventSeriesService {

    private final EventSeriesRepository eventSeriesRepository;
    private final EventSeriesMapper eventSeriesMapper;
    private final OrganizersRepository organizersRepository;
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

    private void assertOwnership(EventSeries series, UUID requesterId) {
        if (series.getOrganizer().getUser() == null || !series.getOrganizer().getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event series");
        }
    }

}
