package com.kryptosystems.ballastasera.cache;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.AttendeeMapper;
import com.kryptosystems.ballastasera.models.mappers.EventSeriesMapper;
import com.kryptosystems.ballastasera.models.mappers.EventsMapperImpl;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.implementations.EventAttendanceServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.EventFlyerProcessingServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.EventSeriesServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.EventsServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.FavoritesServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.OrganizersServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.UsersServiceImpl;
import com.kryptosystems.ballastasera.services.implementations.VenuesServiceImpl;
import com.kryptosystems.ballastasera.services.manager.EmailService;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import com.kryptosystems.ballastasera.services.manager.EventFlyerProcessingService;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.FavoritesService;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache del detalle de evento con un cache en memoria en vez de Redis: la segunda lectura no
 * vuelve a la base de datos y cada escritura borra lo que le toca. Los services son los reales
 * (con sus @CacheEvict) y los repositorios son mocks, asi que "ir a la base de datos" se mide
 * contando las llamadas a eventsRepository.findByIdWithDetails.
 */
@SpringJUnitConfig
@MockitoBean(types = {EventAttendanceRepository.class, FavoritesRepository.class, CitiesRepository.class,
        EventResolverService.class, ObjectStorageService.class, WebpConverterService.class, GeocodingService.class,
        EmailService.class, AttendeeMapper.class, OrganizerMapper.class, VenuesMapper.class, EventSeriesMapper.class})
class EventsCacheTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID MISSING_EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000009");
    private static final UUID VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SERIES_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    @Import({EventsCache.class, EventsMapperImpl.class, EventsServiceImpl.class, EventFlyerProcessingServiceImpl.class,
            FavoritesServiceImpl.class, EventAttendanceServiceImpl.class, UsersServiceImpl.class,
            OrganizersServiceImpl.class, VenuesServiceImpl.class, EventSeriesServiceImpl.class})
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        /** EventsServiceImpl recibe este mock: asi updateFlyer no pasa por el @CacheEvict de convertAndPublish. */
        @Bean
        @Primary
        EventFlyerProcessingService eventFlyerProcessingServiceMock() {
            return mock(EventFlyerProcessingService.class);
        }
    }

    @Autowired
    private EventsCache eventsCache;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EventsService eventsService;

    @Autowired
    private EventFlyerProcessingServiceImpl eventFlyerProcessingService;

    @Autowired
    private FavoritesService favoritesService;

    @Autowired
    private EventAttendanceService eventAttendanceService;

    @Autowired
    private UsersService usersService;

    @Autowired
    private OrganizersService organizersService;

    @Autowired
    private VenuesService venuesService;

    @Autowired
    private EventSeriesService eventSeriesService;

    @MockitoBean
    private EventsRepository eventsRepository;

    @MockitoBean
    private UsersRepository usersRepository;

    @MockitoBean
    private OrganizersRepository organizersRepository;

    @MockitoBean
    private VenuesRepository venuesRepository;

    @MockitoBean
    private EventSeriesRepository eventSeriesRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(RedisCacheConfig.EVENT_DETAIL).clear();

        Users user = new Users();
        user.setId(USER_ID);
        user.setEmail("user@example.com");

        Organizers organizer = new Organizers();
        organizer.setId(ORGANIZER_ID);
        organizer.setUser(user);

        Venues venue = new Venues();
        venue.setId(VENUE_ID);

        EventSeries series = new EventSeries();
        series.setId(SERIES_ID);
        series.setOrganizer(organizer);

        when(eventsRepository.findByIdWithDetails(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, organizer)));
        when(eventsRepository.findByIdWithDetails(OTHER_EVENT_ID)).thenReturn(Optional.of(event(OTHER_EVENT_ID, organizer)));
        when(eventsRepository.findByIdWithDetails(MISSING_EVENT_ID)).thenReturn(Optional.empty());
        when(eventsRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, organizer)));
        when(eventsRepository.existsById(EVENT_ID)).thenReturn(true);
        when(usersRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
    }

    @Test
    void secondReadComesFromCache() {
        eventsCache.findByIdWithDetails(EVENT_ID);
        EventDetailDto cached = eventsCache.findByIdWithDetails(EVENT_ID);

        assertThat(cached.getId()).isEqualTo(EVENT_ID);
        verify(eventsRepository, times(1)).findByIdWithDetails(EVENT_ID);
    }

    @Test
    void notFoundIsNotCached() {
        assertThrows(EntityNotFoundException.class, () -> eventsCache.findByIdWithDetails(MISSING_EVENT_ID));
        assertThrows(EntityNotFoundException.class, () -> eventsCache.findByIdWithDetails(MISSING_EVENT_ID));

        verify(eventsRepository, times(2)).findByIdWithDetails(MISSING_EVENT_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writesToOneEvent")
    void writeToOneEventEvictsOnlyThatEvent(String name, Consumer<EventsCacheTest> write) {
        eventsCache.findByIdWithDetails(EVENT_ID);
        eventsCache.findByIdWithDetails(OTHER_EVENT_ID);

        write.accept(this);
        eventsCache.findByIdWithDetails(EVENT_ID);
        eventsCache.findByIdWithDetails(OTHER_EVENT_ID);

        verify(eventsRepository, times(2)).findByIdWithDetails(EVENT_ID);
        verify(eventsRepository, times(1)).findByIdWithDetails(OTHER_EVENT_ID);
    }

    /** Organizer y venue van dentro del detalle de todos sus eventos: sus escrituras vacian el cache entero. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("writesSharedByManyEvents")
    void writeSharedByManyEventsEvictsEveryEvent(String name, Consumer<EventsCacheTest> write) {
        eventsCache.findByIdWithDetails(EVENT_ID);
        eventsCache.findByIdWithDetails(OTHER_EVENT_ID);

        write.accept(this);
        eventsCache.findByIdWithDetails(EVENT_ID);
        eventsCache.findByIdWithDetails(OTHER_EVENT_ID);

        verify(eventsRepository, times(2)).findByIdWithDetails(EVENT_ID);
        verify(eventsRepository, times(2)).findByIdWithDetails(OTHER_EVENT_ID);
    }

    private static Stream<Arguments> writesToOneEvent() {
        return Stream.of(
                write("event update", t -> t.eventsService.update(EVENT_ID, USER_ID, new EventUpdateDto())),
                write("event updateStatus", t -> t.eventsService.updateStatus(USER_ID, EVENT_ID, EventStatus.CANCELLED)),
                write("event updateFlyer", t -> t.eventsService.updateFlyer(EVENT_ID, USER_ID, image())),
                write("event updateFlyerAsAdmin", t -> t.eventsService.updateFlyerAsAdmin(EVENT_ID, image())),
                write("event deleteFlyer", t -> t.eventsService.deleteFlyer(EVENT_ID, USER_ID)),
                write("event deleteFlyerAsAdmin", t -> t.eventsService.deleteFlyerAsAdmin(EVENT_ID)),
                write("event delete", t -> t.eventsService.delete(EVENT_ID, USER_ID)),
                write("event deleteAsAdmin", t -> t.eventsService.deleteAsAdmin(EVENT_ID)),
                write("event removeVenue", t -> t.eventsService.removeVenue(EVENT_ID, USER_ID)),
                write("flyer convertAndPublish", t -> t.eventFlyerProcessingService.convertAndPublish(EVENT_ID, JPEG_BYTES)),
                write("favorite add", t -> t.favoritesService.addFavorite(USER_ID, EVENT_ID)),
                write("favorite remove", t -> t.favoritesService.removeFavorite(USER_ID, EVENT_ID)),
                write("attendance add", t -> t.eventAttendanceService.addAttendance(USER_ID, EVENT_ID)),
                write("attendance remove", t -> t.eventAttendanceService.removeAttendance(USER_ID, EVENT_ID)));
    }

    private static Stream<Arguments> writesSharedByManyEvents() {
        return Stream.of(
                write("organizer update", t -> t.organizersService.update(ORGANIZER_ID, USER_ID, new OrganizerUpdateDto())),
                write("organizer updateAsAdmin", t -> t.organizersService.updateAsAdmin(ORGANIZER_ID, new OrganizerUpdateDto())),
                write("organizer delete", t -> t.organizersService.delete(ORGANIZER_ID, USER_ID)),
                write("organizer deleteAsAdmin", t -> t.organizersService.deleteAsAdmin(ORGANIZER_ID)),
                write("organizer claim", t -> t.organizersService.claim(ORGANIZER_ID, USER_ID)),
                write("organizer verify", t -> t.organizersService.verify(ORGANIZER_ID)),
                write("venue update", t -> t.venuesService.updateVenueAsAdmin(VENUE_ID, new VenueUpdateDto())),
                write("venue updateLogo", t -> t.venuesService.updateLogoAsAdmin(VENUE_ID, image())),
                write("venue deleteLogo", t -> t.venuesService.deleteLogoAsAdmin(VENUE_ID)),
                write("venue delete", t -> t.venuesService.deleteVenue(VENUE_ID)),
                write("series delete", t -> t.eventSeriesService.delete(SERIES_ID, USER_ID)),
                write("user delete", t -> t.usersService.deleteById(USER_ID)));
    }

    private static Arguments write(String name, Consumer<EventsCacheTest> write) {
        return Arguments.of(name, write);
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("file", JPEG_BYTES);
    }

    private static Events event(UUID id, Organizers organizer) {
        Events event = new Events();
        event.setId(id);
        event.setOrganizer(organizer);
        event.setStartAt(OffsetDateTime.now().plusDays(2));
        return event;
    }
}
