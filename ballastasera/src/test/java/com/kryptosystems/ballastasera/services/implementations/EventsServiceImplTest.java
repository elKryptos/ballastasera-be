package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.exceptions.VenueCityMismatchException;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventsServiceImplTest {

    private static final UUID REQUESTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORGANIZER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORGANIZER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SERIES_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Mock
    private EventsRepository eventsRepository;

    @Mock
    private EventAttendanceRepository eventAttendanceRepository;

    @Mock
    private EventsMapper eventsMapper;

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private EventResolverService eventResolverService;

    @InjectMocks
    private EventsServiceImpl eventsService;

    @Test
    void createSavesPendingEventForVerifiedOrganizerOwner() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        Events mappedEvent = mappedEvent();

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(new Cities());
        when(eventsRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(eventsRepository.save(mappedEvent)).thenReturn(mappedEvent);

        Events result = eventsService.create(REQUESTER_ID, dto);

        assertSame(mappedEvent, result);
        assertSame(organizer, result.getOrganizer());
        assertEquals(EventStatus.PENDING, result.getStatus());
        verify(eventsRepository).save(mappedEvent);
    }

    @Test
    void createRejectsOrganizerOwnedByAnotherUser() {
        Organizers organizer = organizer(ORGANIZER_ID, OTHER_USER_ID, true);
        EventCreateDto dto = createDto(ORGANIZER_ID);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.create(REQUESTER_ID, dto)
        );

        verify(eventsMapper, never()).toEventEntity(any(EventCreateDto.class));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void createRejectsUnverifiedOrganizer() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, false);
        EventCreateDto dto = createDto(ORGANIZER_ID);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.create(REQUESTER_ID, dto)
        );

        verify(eventsMapper, never()).toEventEntity(any(EventCreateDto.class));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void createAllowsVenueFromMatchingCityRegardlessOfOwner() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        Organizers otherOrganizer = organizer(OTHER_ORGANIZER_ID, OTHER_USER_ID, true);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        dto.setVenueId(VENUE_ID);
        Events mappedEvent = mappedEvent();
        Cities city = new Cities();
        city.setId(1L);
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        venue.setCity(city);
        venue.setOrganizer(otherOrganizer);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(city);
        when(eventResolverService.resolveVenue(VENUE_ID, 1L)).thenReturn(venue);
        when(eventsRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(eventsRepository.save(mappedEvent)).thenReturn(mappedEvent);

        Events result = eventsService.create(REQUESTER_ID, dto);

        assertSame(venue, result.getVenue());
        verify(eventsRepository).save(mappedEvent);
    }

    @Test
    void createRejectsVenueFromDifferentCity() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        dto.setVenueId(VENUE_ID);
        Cities city = new Cities();
        city.setId(1L);
        Cities otherCity = new Cities();
        otherCity.setId(2L);
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        venue.setCity(otherCity);
        Events mappedEvent = mappedEvent();

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(city);
        when(eventResolverService.resolveVenue(VENUE_ID, 1L))
                .thenThrow(new VenueCityMismatchException("Venue belongs to another city"));

        assertThrows(
                VenueCityMismatchException.class,
                () -> eventsService.create(REQUESTER_ID, dto)
        );

        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void createRejectsSeriesOwnedByAnotherOrganizer() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        Organizers otherOrganizer = organizer(OTHER_ORGANIZER_ID, OTHER_USER_ID, true);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        dto.setSeriesId(SERIES_ID);
        EventSeries series = new EventSeries();
        series.setId(SERIES_ID);
        series.setOrganizer(otherOrganizer);
        Events mappedEvent = mappedEvent();

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(new Cities());
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.create(REQUESTER_ID, dto)
        );

        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateSavesEventForOwner() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        Events event = event(organizer);
        EventUpdateDto dto = new EventUpdateDto();

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.update(EVENT_ID, REQUESTER_ID, dto);

        assertSame(event, result);
        verify(eventsRepository).save(event);
    }

    @Test
    void updateRejectsVenueFromDifferentCity() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        Events event = event(organizer);
        Cities city = new Cities();
        city.setId(1L);
        event.setCity(city);
        EventUpdateDto dto = new EventUpdateDto();
        dto.setVenueId(VENUE_ID);
        Cities otherCity = new Cities();
        otherCity.setId(2L);
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        venue.setCity(otherCity);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventResolverService.resolveVenue(VENUE_ID, 1L))
                .thenThrow(new VenueCityMismatchException("Venue belongs to another city"));

        assertThrows(
                VenueCityMismatchException.class,
                () -> eventsService.update(EVENT_ID, REQUESTER_ID, dto)
        );

        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateRejectsSeriesOwnedByAnotherOrganizer() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        Organizers otherOrganizer = organizer(OTHER_ORGANIZER_ID, OTHER_USER_ID, true);
        Events event = event(organizer);
        EventUpdateDto dto = new EventUpdateDto();
        dto.setSeriesId(SERIES_ID);
        EventSeries series = new EventSeries();
        series.setId(SERIES_ID);
        series.setOrganizer(otherOrganizer);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.update(EVENT_ID, REQUESTER_ID, dto)
        );

        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateRejectsOperationForAnotherUser() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.update(EVENT_ID, OTHER_USER_ID, new EventUpdateDto())
        );

        verify(eventsMapper, never()).updateEventEntityFromDto(any(EventUpdateDto.class), any(Events.class));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateStatusSavesEventForOwner() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.updateStatus(REQUESTER_ID, EVENT_ID, EventStatus.PUBLISHED);

        assertSame(event, result);
        assertEquals(EventStatus.PUBLISHED, event.getStatus());
        verify(eventsRepository).save(event);
    }

    @Test
    void updateStatusRejectsOperationForAnotherUser() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.updateStatus(OTHER_USER_ID, EVENT_ID, EventStatus.PUBLISHED)
        );

        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void deleteRemovesEventForOwner() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        eventsService.delete(EVENT_ID, REQUESTER_ID);

        verify(eventsRepository).delete(event);
    }

    @Test
    void deleteRejectsOperationForAnotherUser() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.delete(EVENT_ID, OTHER_USER_ID)
        );

        verify(eventsRepository, never()).delete(any(Events.class));
    }

    private EventCreateDto createDto(UUID organizerId) {
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(2);
        EventCreateDto dto = new EventCreateDto();
        dto.setOrganizerId(organizerId);
        dto.setCityId(1L);
        dto.setTitle("Salsa Night");
        dto.setStartAt(startAt);
        dto.setEndAt(startAt.plusHours(3));
        dto.setAddress("Via Roma 1");
        dto.setLatitude(45.4642);
        dto.setLongitude(9.1900);
        return dto;
    }

    private Events mappedEvent() {
        Events event = new Events();
        event.setTitle("Salsa Night");
        event.setAddress("Via Roma 1");
        event.setLatitude(45.4642);
        event.setLongitude(9.1900);
        return event;
    }

    private Events event(Organizers organizer) {
        Events event = mappedEvent();
        event.setId(EVENT_ID);
        event.setOrganizer(organizer);
        event.setStartAt(OffsetDateTime.now().plusDays(2));
        event.setEndAt(OffsetDateTime.now().plusDays(2).plusHours(3));
        return event;
    }

    private Organizers organizer(UUID organizerId, UUID userId, boolean verified) {
        Users user = new Users();
        user.setId(userId);

        Organizers organizer = new Organizers();
        organizer.setId(organizerId);
        organizer.setUser(user);
        organizer.setVerified(verified);
        return organizer;
    }
}
