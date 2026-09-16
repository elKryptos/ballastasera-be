package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.exceptions.VenueCityMismatchException;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerEventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerEventSummaryDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.models.mappers.DanceStylesMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.EventFlyerProcessingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private DanceStylesMapper danceStylesMapper;

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

    @InjectMocks
    private EventsServiceImpl eventsService;

    @Test
    void getEventDetailReturnsPublishedPastEvent() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        event.setStatus(EventStatus.PUBLISHED);
        event.setStartAt(OffsetDateTime.now().minusDays(2));
        event.setEndAt(OffsetDateTime.now().minusDays(1));
        EventDetailDto detail = new EventDetailDto();

        when(eventsRepository.findByIdWithDetails(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventsMapper.toEventDetailDto(event)).thenReturn(detail);

        assertSame(detail, eventsService.getEventDetail(EVENT_ID));
    }

    @Test
    void getEventDetailHidesNonPublishedEvent() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        event.setStatus(EventStatus.PENDING);
        when(eventsRepository.findByIdWithDetails(EVENT_ID)).thenReturn(Optional.of(event));

        assertThrows(EntityNotFoundException.class, () -> eventsService.getEventDetail(EVENT_ID));
        verify(eventsMapper, never()).toEventDetailDto(event);
    }

    @Test
    void getManageableEventDetailReturnsRawInstagramValue() {
        Events event = event(organizer(ORGANIZER_ID, REQUESTER_ID, true));
        event.getOrganizer().setInstagram("https://instagram.com/organizer");
        event.setInstagramUrl(null);
        OrganizerEventDetailDto detail = stubManageableDetail(event);

        when(eventsRepository.findByIdWithDetails(EVENT_ID)).thenReturn(Optional.of(event));

        assertSame(detail, eventsService.getManageableEventDetail(REQUESTER_ID, EVENT_ID));
        assertNull(detail.getInstagramUrl());
    }

    @Test
    void findManageableByOrganizerIdFiltersByStatusForOwner() {
        Organizers organizer = organizer(ORGANIZER_ID, REQUESTER_ID, true);
        Events event = event(organizer);
        OrganizerEventSummaryDto summary = new OrganizerEventSummaryDto();
        PageRequest pageable = PageRequest.of(0, 20);

        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(eventsRepository.findManageableByOrganizerIdAndStatus(ORGANIZER_ID, EventStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventsMapper.toOrganizerEventSummaryDto(event)).thenReturn(summary);

        var result = eventsService.findManageableByOrganizerId(
                REQUESTER_ID, ORGANIZER_ID, EventStatus.PENDING, pageable);

        assertEquals(1, result.getTotalElements());
        assertSame(summary, result.getContent().getFirst());
    }

    @Test
    void findManageableByOrganizerIdRejectsAnotherUser() {
        Organizers organizer = organizer(ORGANIZER_ID, OTHER_USER_ID, true);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThrows(AccessDeniedException.class, () -> eventsService.findManageableByOrganizerId(
                REQUESTER_ID, ORGANIZER_ID, null, PageRequest.of(0, 20)));
        verify(eventsRepository, never()).findManageableByOrganizerId(any(UUID.class), any());
    }

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
        OrganizerEventDetailDto response = stubManageableDetail(mappedEvent);

        OrganizerEventDetailDto result = eventsService.create(REQUESTER_ID, dto);

        assertSame(response, result);
        assertSame(organizer, mappedEvent.getOrganizer());
        assertEquals(EventStatus.PENDING, mappedEvent.getStatus());
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
        stubManageableDetail(mappedEvent);

        eventsService.create(REQUESTER_ID, dto);

        assertSame(venue, mappedEvent.getVenue());
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
        OrganizerEventDetailDto response = stubManageableDetail(event);

        OrganizerEventDetailDto result = eventsService.update(EVENT_ID, REQUESTER_ID, dto);

        assertSame(response, result);
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
        OrganizerEventDetailDto response = stubManageableDetail(event);

        OrganizerEventDetailDto result = eventsService.updateStatus(REQUESTER_ID, EVENT_ID, EventStatus.PUBLISHED);

        assertSame(response, result);
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
        OrganizerEventDetailDto response = stubManageableDetail(event);

        OrganizerEventDetailDto result = eventsService.updateFlyer(EVENT_ID, REQUESTER_ID, file);

        assertSame(response, result);
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
        OrganizerEventDetailDto response = stubManageableDetail(event);

        OrganizerEventDetailDto result = eventsService.deleteFlyer(EVENT_ID, REQUESTER_ID);

        assertSame(response, result);
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
        event.setStartAt(OffsetDateTime.now().plusDays(2));
        event.setEndAt(OffsetDateTime.now().plusDays(2).plusHours(3));
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

    private OrganizerEventDetailDto stubManageableDetail(Events event) {
        OrganizerEventDetailDto detail = new OrganizerEventDetailDto();
        when(eventsMapper.toOrganizerEventDetailDto(event)).thenReturn(detail);
        return detail;
    }
}
