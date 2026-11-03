package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.OrganizerType;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventSeriesUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.EventSeries;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.EventSeriesMapper;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.EventResolverService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventSeriesServiceImplTest {

    private static final UUID REQUESTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SERIES_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private EventSeriesMapper eventSeriesMapper;

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private EventsRepository eventsRepository;

    @Mock
    private EventResolverService eventResolverService;

    @Mock
    private WebpConverterService webpConverterService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private VenuesRepository venuesRepository;

    @InjectMocks
    private EventSeriesServiceImpl eventSeriesService;

    // ---------- D12: CLUB y VENUE solo publican en sus venues ----------

    @Test
    void createAllowsAClubInItsOwnVenue() {
        Organizers club = organizer(OrganizerType.CLUB);
        EventSeriesCreateDto dto = createDto(VENUE_ID);
        EventSeries mapped = mappedSeries();
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(club));
        when(venuesRepository.existsByIdAndOrganizerId(VENUE_ID, ORGANIZER_ID)).thenReturn(true);
        stubBuildAndSave(dto, mapped);

        assertThat(eventSeriesService.create(REQUESTER_ID, dto)).isSameAs(mapped);
        assertThat(mapped.getOrganizer()).isSameAs(club);
    }

    @Test
    void createRejectsAClubInAVenueOfAnotherOrganizer() {
        EventSeriesCreateDto dto = createDto(VENUE_ID);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer(OrganizerType.CLUB)));
        when(venuesRepository.existsByIdAndOrganizerId(VENUE_ID, ORGANIZER_ID)).thenReturn(false);

        assertThatThrownBy(() -> eventSeriesService.create(REQUESTER_ID, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Venue not owned by this organizer");
        verifyNoInteractions(eventSeriesMapper);
        verify(eventSeriesRepository, never()).save(any());
    }

    @Test
    void createRejectsAVenueOrganizerWithoutVenue() {
        EventSeriesCreateDto dto = createDto(null);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer(OrganizerType.VENUE)));

        assertThatThrownBy(() -> eventSeriesService.create(REQUESTER_ID, dto))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(venuesRepository, eventSeriesMapper);
    }

    @Test
    void createAllowsAPersonInAnyVenue() {
        EventSeriesCreateDto dto = createDto(VENUE_ID);
        EventSeries mapped = mappedSeries();
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer(OrganizerType.PERSON)));
        stubBuildAndSave(dto, mapped);

        assertThat(eventSeriesService.create(REQUESTER_ID, dto)).isSameAs(mapped);
        verifyNoInteractions(venuesRepository);
    }

    @Test
    void createAsAdminDoesNotApplyTheVenueRule() {
        EventSeriesCreateDto dto = createDto(VENUE_ID);
        EventSeries mapped = mappedSeries();
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer(OrganizerType.CLUB)));
        stubBuildAndSave(dto, mapped);

        assertThat(eventSeriesService.createAsAdmin(dto)).isSameAs(mapped);
        verifyNoInteractions(venuesRepository);
    }

    @Test
    void updateRejectsAClubMovingTheSeriesToAVenueOfAnotherOrganizer() {
        EventSeries series = series(organizer(OrganizerType.CLUB));
        EventSeriesUpdateDto dto = new EventSeriesUpdateDto();
        dto.setVenueId(VENUE_ID);
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(venuesRepository.existsByIdAndOrganizerId(VENUE_ID, ORGANIZER_ID)).thenReturn(false);

        assertThatThrownBy(() -> eventSeriesService.update(SERIES_ID, REQUESTER_ID, dto))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(eventSeriesMapper);
        verify(eventSeriesRepository, never()).save(any());
    }

    /** Sin venueId en el body no se mira la regla: el venue no cambia. */
    @Test
    void updateWithoutVenueChangeSkipsTheVenueRule() {
        EventSeries series = series(organizer(OrganizerType.CLUB));
        EventSeriesUpdateDto dto = new EventSeriesUpdateDto();
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(eventSeriesRepository.save(series)).thenReturn(series);

        eventSeriesService.update(SERIES_ID, REQUESTER_ID, dto);

        verifyNoInteractions(venuesRepository);
    }

    @Test
    void removeVenueRejectsAClub() {
        EventSeries series = series(organizer(OrganizerType.CLUB));
        Venues venue = new Venues();
        series.setVenue(venue);
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

        assertThatThrownBy(() -> eventSeriesService.removeVenue(SERIES_ID, REQUESTER_ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(series.getVenue()).isSameAs(venue);
        verify(eventSeriesRepository, never()).save(any());
    }

    // ---------- series sin organizador (ON DELETE SET NULL) ----------

    @Test
    void detailOfASeriesWithoutOrganizerHasNoInstagramFallback() {
        EventSeries series = series(null);
        when(eventSeriesRepository.findByIdWithDetails(SERIES_ID)).thenReturn(Optional.of(series));
        when(eventSeriesMapper.toEventSeriesDetailDto(series)).thenReturn(new EventSeriesDetailDto());

        assertThat(eventSeriesService.getEventSeriesDetail(SERIES_ID).getInstagramUrl()).isNull();
    }

    @Test
    void detailFallsBackToTheOrganizerInstagram() {
        Organizers organizer = organizer(OrganizerType.PERSON);
        organizer.setInstagram("salsaloca.milano");
        EventSeries series = series(organizer);
        when(eventSeriesRepository.findByIdWithDetails(SERIES_ID)).thenReturn(Optional.of(series));
        when(eventSeriesMapper.toEventSeriesDetailDto(series)).thenReturn(new EventSeriesDetailDto());

        assertThat(eventSeriesService.getEventSeriesDetail(SERIES_ID).getInstagramUrl()).isEqualTo("salsaloca.milano");
    }

    /** Antes daba 500 (NPE en assertOwnership). */
    @Test
    void updateOfASeriesWithoutOrganizerIsForbidden() {
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(null)));

        assertThatThrownBy(() -> eventSeriesService.update(SERIES_ID, REQUESTER_ID, new EventSeriesUpdateDto()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not the owner of this event series");
    }

    private void stubBuildAndSave(EventSeriesCreateDto dto, EventSeries mapped) {
        Cities city = new Cities();
        city.setId(1L);
        when(eventSeriesMapper.toEventSeriesEntity(dto)).thenReturn(mapped);
        when(eventResolverService.resolveCity(1L)).thenReturn(city);
        when(eventResolverService.resolveVenue(dto.getVenueId(), 1L)).thenReturn(new Venues());
        when(eventResolverService.resolveDanceStyles(any())).thenReturn(new HashSet<>());
        when(eventSeriesRepository.save(mapped)).thenReturn(mapped);
    }

    private static EventSeriesCreateDto createDto(UUID venueId) {
        EventSeriesCreateDto dto = new EventSeriesCreateDto();
        dto.setOrganizerId(ORGANIZER_ID);
        dto.setVenueId(venueId);
        dto.setCityId(1L);
        dto.setTitle("Social del venerdì");
        return dto;
    }

    /** Con coordenadas: no pasa por el geocoding. */
    private static EventSeries mappedSeries() {
        EventSeries series = new EventSeries();
        series.setTitle("Social del venerdì");
        series.setAddress("Via Borsieri 12");
        series.setLatitude(45.4889);
        series.setLongitude(9.1890);
        return series;
    }

    private static EventSeries series(Organizers organizer) {
        EventSeries series = mappedSeries();
        series.setId(SERIES_ID);
        series.setOrganizer(organizer);
        series.setCity(new Cities());
        return series;
    }

    private static Organizers organizer(OrganizerType type) {
        Users owner = new Users();
        owner.setId(REQUESTER_ID);
        Organizers organizer = new Organizers();
        organizer.setId(ORGANIZER_ID);
        organizer.setUser(owner);
        organizer.setType(type);
        organizer.setVerified(true);
        return organizer;
    }
}
