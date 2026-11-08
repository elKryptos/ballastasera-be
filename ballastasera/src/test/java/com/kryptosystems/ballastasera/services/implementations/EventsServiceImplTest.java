package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventManagementTab;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.enums.OrganizerType;
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
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.EventFlyerProcessingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private EventsMapper eventsMapper;

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private EventResolverService eventResolverService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private EventFlyerProcessingService eventFlyerProcessingService;

    @Mock
    private VenuesRepository venuesRepository;

    @InjectMocks
    private EventsServiceImpl eventsService;

    // ---------- D12: CLUB y VENUE solo publican en sus venues ----------

    @Test
    void createAllowsAClubInItsOwnVenue() {
        Organizers club = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        club.setType(OrganizerType.CLUB);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        dto.setVenueId(VENUE_ID);
        Events mappedEvent = mappedEvent();
        Cities city = new Cities();
        city.setId(1L);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(club));
        when(venuesRepository.existsByIdAndOrganizerId(VENUE_ID, ORGANIZER_ID)).thenReturn(true);
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(city);
        when(eventsRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(eventsRepository.save(mappedEvent)).thenReturn(mappedEvent);

        assertSame(mappedEvent, eventsService.create(REQUESTER_ID, dto));
    }

    @Test
    void createRejectsAClubInAVenueOfAnotherOrganizer() {
        Organizers club = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        club.setType(OrganizerType.CLUB);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        dto.setVenueId(VENUE_ID);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(club));
        when(venuesRepository.existsByIdAndOrganizerId(VENUE_ID, ORGANIZER_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> eventsService.create(REQUESTER_ID, dto));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void createRejectsAVenueOrganizerWithoutVenue() {
        Organizers venueOrganizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        venueOrganizer.setType(OrganizerType.VENUE);
        EventCreateDto dto = createDto(ORGANIZER_ID);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(venueOrganizer));

        assertThrows(AccessDeniedException.class, () -> eventsService.create(REQUESTER_ID, dto));
        verify(eventsRepository, never()).save(any(Events.class));
        verifyNoInteractions(venuesRepository);
    }

    /** SCHOOL no está atada a su venue (puede dar un social en otro local). */
    @Test
    void createAllowsASchoolInAnyVenue() {
        Organizers school = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        school.setType(OrganizerType.SCHOOL);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        dto.setVenueId(VENUE_ID);
        Events mappedEvent = mappedEvent();
        Cities city = new Cities();
        city.setId(1L);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(school));
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(city);
        when(eventsRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(eventsRepository.save(mappedEvent)).thenReturn(mappedEvent);

        assertSame(mappedEvent, eventsService.create(REQUESTER_ID, dto));
        verifyNoInteractions(venuesRepository);
    }

    @Test
    void createAsAdminDoesNotApplyTheVenueRule() {
        Organizers club = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        club.setType(OrganizerType.CLUB);
        EventCreateDto dto = createDto(ORGANIZER_ID);
        Events mappedEvent = mappedEvent();

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(club));
        when(eventsMapper.toEventEntity(dto)).thenReturn(mappedEvent);
        when(eventResolverService.resolveCity(1L)).thenReturn(new Cities());
        when(eventsRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(eventsRepository.save(mappedEvent)).thenReturn(mappedEvent);

        assertSame(mappedEvent, eventsService.createAsAdmin(dto));
        verifyNoInteractions(venuesRepository);
    }

    @Test
    void updateRejectsAClubMovingTheEventToAVenueOfAnotherOrganizer() {
        Organizers club = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        club.setType(OrganizerType.CLUB);
        Events event = event(club);
        EventUpdateDto dto = new EventUpdateDto();
        dto.setVenueId(VENUE_ID);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(venuesRepository.existsByIdAndOrganizerId(VENUE_ID, ORGANIZER_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> eventsService.update(EVENT_ID, REQUESTER_ID, dto));
        verify(eventsMapper, never()).updateEventEntityFromDto(any(EventUpdateDto.class), any(Events.class));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void removeVenueRejectsAClub() {
        Organizers club = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        club.setType(OrganizerType.CLUB);
        Events event = event(club);
        Venues venue = new Venues();
        event.setVenue(venue);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(AccessDeniedException.class, () -> eventsService.removeVenue(EVENT_ID, REQUESTER_ID));
        assertSame(venue, event.getVenue());
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void removeVenueStillWorksForAPerson() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        event.setVenue(new Venues());

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        eventsService.removeVenue(EVENT_ID, REQUESTER_ID);

        assertEquals(null, event.getVenue());
        verifyNoInteractions(venuesRepository);
    }

    @Test
    void createSavesPublishedEventForVerifiedOrganizerOwner() {
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
        assertEquals(EventStatus.PUBLISHED, result.getStatus());
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
    void updateFlyerStoresRawFileAndStartsProcessing() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        byte[] content = new byte[]{1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "flyer.jpg", "image/jpeg", content);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.updateFlyer(EVENT_ID, REQUESTER_ID, file);

        assertSame(event, result);
        assertEquals(FlyerStatus.PROCESSING, event.getFlyerStatus());
        verify(objectStorageService).uploadEventFlyerRaw(eq(EVENT_ID), any(byte[].class));
        verify(eventsRepository).save(event);
        verify(eventFlyerProcessingService).convertAndPublish(eq(EVENT_ID), any(byte[].class));
    }

    @Test
    void updateFlyerRejectsOperationForAnotherUser() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        MockMultipartFile file = new MockMultipartFile("file", "flyer.jpg", "image/jpeg", new byte[]{1});

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.updateFlyer(EVENT_ID, OTHER_USER_ID, file)
        );

        verify(objectStorageService, never()).uploadEventFlyerRaw(any(UUID.class), any(byte[].class));
        verify(eventsRepository, never()).save(any(Events.class));
        verify(eventFlyerProcessingService, never()).convertAndPublish(any(UUID.class), any(byte[].class));
    }

    @Test
    void updateFlyerAsAdminStoresRawFileAndStartsProcessing() {
        Events event = event(organizer(ORGANIZER_ID, OTHER_USER_ID, true));
        byte[] content = new byte[]{1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "flyer.jpg", "image/jpeg", content);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.updateFlyerAsAdmin(EVENT_ID, file);

        assertSame(event, result);
        assertEquals(FlyerStatus.PROCESSING, event.getFlyerStatus());
        verify(objectStorageService).uploadEventFlyerRaw(eq(EVENT_ID), any(byte[].class));
        verify(eventsRepository).save(event);
        verify(eventFlyerProcessingService).convertAndPublish(eq(EVENT_ID), any(byte[].class));
    }

    @Test
    void updateFlyerAsAdminRejectsMissingEvent() {
        MockMultipartFile file = new MockMultipartFile("file", "flyer.jpg", "image/jpeg", new byte[]{1});
        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventsService.updateFlyerAsAdmin(EVENT_ID, file)
        );

        verify(objectStorageService, never()).uploadEventFlyerRaw(any(UUID.class), any(byte[].class));
        verify(eventsRepository, never()).save(any(Events.class));
        verify(eventFlyerProcessingService, never()).convertAndPublish(any(UUID.class), any(byte[].class));
    }

    @Test
    void deleteFlyerRemovesStoredFilesAndResetsStatusForOwner() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        event.setFlyerStatus(FlyerStatus.READY);
        event.setFlyerUrl("https://cdn.example.com/flyer.webp");

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.deleteFlyer(EVENT_ID, REQUESTER_ID);

        assertSame(event, result);
        assertEquals(FlyerStatus.NONE, event.getFlyerStatus());
        assertEquals(null, event.getFlyerUrl());
        verify(objectStorageService).deleteEventFlyerRaw(EVENT_ID);
        verify(objectStorageService).deleteEventFlyerFinal(EVENT_ID);
        verify(eventsRepository).save(event);
    }

    @Test
    void deleteFlyerRejectsOperationForAnotherUser() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(
                AccessDeniedException.class,
                () -> eventsService.deleteFlyer(EVENT_ID, OTHER_USER_ID)
        );

        verify(objectStorageService, never()).deleteEventFlyerRaw(any(UUID.class));
        verify(objectStorageService, never()).deleteEventFlyerFinal(any(UUID.class));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void deleteFlyerAsAdminRemovesStoredFilesAndResetsStatus() {
        Events event = event(organizer(ORGANIZER_ID, OTHER_USER_ID, true));
        event.setFlyerStatus(FlyerStatus.READY);
        event.setFlyerUrl("https://cdn.example.com/flyer.webp");

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.deleteFlyerAsAdmin(EVENT_ID);

        assertSame(event, result);
        assertEquals(FlyerStatus.NONE, event.getFlyerStatus());
        verify(objectStorageService).deleteEventFlyerRaw(EVENT_ID);
        verify(objectStorageService).deleteEventFlyerFinal(EVENT_ID);
        verify(eventsRepository).save(event);
    }

    @Test
    void deleteFlyerAsAdminRejectsMissingEvent() {
        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> eventsService.deleteFlyerAsAdmin(EVENT_ID)
        );

        verify(objectStorageService, never()).deleteEventFlyerRaw(any(UUID.class));
        verify(objectStorageService, never()).deleteEventFlyerFinal(any(UUID.class));
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

    // ---------- Admin: sin chequeo de ownership ni D12 ----------

    @Test
    void updateAsAdminEditsAnEventOfAnotherUserWithoutTheVenueRule() {
        Organizers club = organizer(ORGANIZER_ID, OTHER_USER_ID, true);
        club.setType(OrganizerType.CLUB);
        Events event = event(club);
        Cities city = new Cities();
        city.setId(1L);
        event.setCity(city);
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        EventUpdateDto dto = new EventUpdateDto();
        dto.setVenueId(VENUE_ID);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventResolverService.resolveVenue(VENUE_ID, 1L)).thenReturn(venue);
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.updateAsAdmin(EVENT_ID, dto);

        assertSame(event, result);
        assertSame(venue, event.getVenue());
        verify(eventsMapper).updateEventEntityFromDto(dto, event);
        verifyNoInteractions(venuesRepository);
    }

    /** organizer_id es ON DELETE SET NULL: el admin puede editar un evento que se quedó sin organizador. */
    @Test
    void updateAsAdminLinksAnOrphanEventToAnOrphanSeries() {
        Events event = event(null);
        EventSeries series = new EventSeries();
        series.setId(SERIES_ID);
        EventUpdateDto dto = new EventUpdateDto();
        dto.setSeriesId(SERIES_ID);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(eventsRepository.save(event)).thenReturn(event);

        eventsService.updateAsAdmin(EVENT_ID, dto);

        assertSame(series, event.getSeries());
    }

    @Test
    void updateAsAdminRejectsASeriesOfAnotherOrganizerForAnOrphanEvent() {
        Events event = event(null);
        EventSeries series = new EventSeries();
        series.setId(SERIES_ID);
        series.setOrganizer(organizer(OTHER_ORGANIZER_ID, OTHER_USER_ID, true));
        EventUpdateDto dto = new EventUpdateDto();
        dto.setSeriesId(SERIES_ID);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

        assertThrows(AccessDeniedException.class, () -> eventsService.updateAsAdmin(EVENT_ID, dto));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateAsAdminRejectsASeriesOfAnotherOrganizer() {
        Events event = event(organizer(ORGANIZER_ID, OTHER_USER_ID, true));
        EventSeries series = new EventSeries();
        series.setId(SERIES_ID);
        series.setOrganizer(organizer(OTHER_ORGANIZER_ID, OTHER_USER_ID, true));
        EventUpdateDto dto = new EventUpdateDto();
        dto.setSeriesId(SERIES_ID);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

        assertThrows(AccessDeniedException.class, () -> eventsService.updateAsAdmin(EVENT_ID, dto));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateAsAdminRejectsMissingEvent() {
        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> eventsService.updateAsAdmin(EVENT_ID, new EventUpdateDto()));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void updateStatusAsAdminChangesTheStatusOfAnEventOfAnotherUser() {
        Events event = event(organizer(ORGANIZER_ID, OTHER_USER_ID, true));
        event.setStatus(EventStatus.PUBLISHED);

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsRepository.save(event)).thenReturn(event);

        Events result = eventsService.updateStatusAsAdmin(EVENT_ID, EventStatus.CANCELLED);

        assertSame(event, result);
        assertEquals(EventStatus.CANCELLED, event.getStatus());
    }

    @Test
    void updateStatusAsAdminRejectsMissingEvent() {
        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> eventsService.updateStatusAsAdmin(EVENT_ID, EventStatus.CANCELLED));
        verify(eventsRepository, never()).save(any(Events.class));
    }

    @Test
    void deleteAsAdminRemovesAnEventOfAnotherUserAndItsFlyers() {
        Events event = event(organizer(ORGANIZER_ID, OTHER_USER_ID, true));

        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        eventsService.deleteAsAdmin(EVENT_ID);

        verify(eventsRepository).delete(event);
        verify(objectStorageService).deleteEventFlyerRaw(EVENT_ID);
        verify(objectStorageService).deleteEventFlyerFinal(EVENT_ID);
    }

    @Test
    void deleteAsAdminRejectsMissingEvent() {
        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> eventsService.deleteAsAdmin(EVENT_ID));
        verify(eventsRepository, never()).delete(any(Events.class));
        verifyNoInteractions(objectStorageService);
    }

    // ---------- Gestión de serate: findForManagement / findMineForManagement ----------

    @Test
    void findForManagementUpcomingFiltersActiveNotEndedEventsInAscendingOrder() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(eventsRepository.findIdsForManagement(eq(EventManagementTab.UPCOMING.getStatuses()), eq(false),
                any(OffsetDateTime.class), eq(ORGANIZER_ID), isNull(), eq("%salsa night%"), pageable.capture()))
                .thenReturn(Page.empty());

        eventsService.findForManagement(EventManagementTab.UPCOMING, ORGANIZER_ID, "  Salsa Night ", PageRequest.of(2, 10));

        assertEquals(Set.of(EventStatus.DRAFT, EventStatus.PENDING, EventStatus.PUBLISHED),
                EventManagementTab.UPCOMING.getStatuses());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.ASC, "startAt").and(Sort.by(Sort.Direction.ASC, "id")),
                pageable.getValue().getSort());
    }

    @Test
    void findForManagementCancelledFiltersOnlyCancelledNotEndedEventsInAscendingOrder() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(eventsRepository.findIdsForManagement(eq(Set.of(EventStatus.CANCELLED)), eq(false),
                any(OffsetDateTime.class), isNull(), isNull(), eq("%"), pageable.capture()))
                .thenReturn(Page.empty());

        eventsService.findForManagement(EventManagementTab.CANCELLED, null, null, PageRequest.of(0, 20));

        assertEquals(Sort.Direction.ASC, pageable.getValue().getSort().getOrderFor("startAt").getDirection());
    }

    @Test
    void findForManagementPastFiltersEndedEventsOfAnyStatusNewestFirst() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(eventsRepository.findIdsForManagement(eq(EnumSet.allOf(EventStatus.class)), eq(true),
                any(OffsetDateTime.class), isNull(), isNull(), eq("%"), pageable.capture()))
                .thenReturn(Page.empty());

        eventsService.findForManagement(EventManagementTab.PAST, null, "   ", PageRequest.of(0, 20));

        assertEquals(Sort.by(Sort.Direction.DESC, "startAt").and(Sort.by(Sort.Direction.DESC, "id")),
                pageable.getValue().getSort());
    }

    @Test
    void findMineForManagementFiltersByOwnerWithoutSearch() {
        when(eventsRepository.findIdsForManagement(eq(EventManagementTab.UPCOMING.getStatuses()), eq(false),
                any(OffsetDateTime.class), isNull(), eq(REQUESTER_ID), eq("%"), any(Pageable.class)))
                .thenReturn(Page.empty());

        eventsService.findMineForManagement(REQUESTER_ID, EventManagementTab.UPCOMING, PageRequest.of(0, 20));

        verify(eventsRepository).findIdsForManagement(eq(EventManagementTab.UPCOMING.getStatuses()), eq(false),
                any(OffsetDateTime.class), isNull(), eq(REQUESTER_ID), eq("%"), any(Pageable.class));
    }

    /** Usuario sin página de organizador: la query no encuentra nada y no se cargan detalles. */
    @Test
    void findMineForManagementReturnsAnEmptyPageWithoutLoadingDetails() {
        when(eventsRepository.findIdsForManagement(any(), eq(false), any(OffsetDateTime.class), isNull(),
                eq(REQUESTER_ID), eq("%"), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<UUID>(List.of(), invocation.getArgument(6), 0));

        Page<Events> result = eventsService.findMineForManagement(REQUESTER_ID, EventManagementTab.UPCOMING,
                PageRequest.of(0, 20));

        assertEquals(0, result.getTotalElements());
        assertEquals(List.of(), result.getContent());
        verify(eventsRepository, never()).findAllWithDetailsByIdIn(any());
    }

    /** loadInOrder: los detalles llegan en cualquier orden, la página respeta el de los ids y su total. */
    @Test
    void findForManagementKeepsTheOrderOfTheIdsAndTheTotal() {
        UUID firstId = UUID.fromString("20000000-0000-0000-0000-00000000000b");
        UUID secondId = UUID.fromString("20000000-0000-0000-0000-00000000000a");
        UUID missingId = UUID.fromString("20000000-0000-0000-0000-00000000000c");
        Events first = new Events();
        first.setId(firstId);
        Events second = new Events();
        second.setId(secondId);

        when(eventsRepository.findIdsForManagement(any(), eq(false), any(OffsetDateTime.class), isNull(), isNull(),
                eq("%"), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(firstId, secondId, missingId),
                        invocation.getArgument(6), 45));
        when(eventsRepository.findAllWithDetailsByIdIn(List.of(firstId, secondId, missingId)))
                .thenReturn(List.of(second, first));

        Page<Events> result = eventsService.findForManagement(EventManagementTab.UPCOMING, null, null,
                PageRequest.of(1, 3));

        assertEquals(List.of(first, second), result.getContent());
        assertEquals(45, result.getTotalElements());
        assertEquals(1, result.getNumber());
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
