package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.exceptions.VenueHasActiveEventsException;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenuesServiceImplTest {

    private static final UUID VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] WEBP_BYTES = {1, 2, 3};
    private static final String OLD_LOGO_URL = "http://localhost/logos/venues/" + VENUE_ID + "/logo-old.webp";
    private static final String NEW_LOGO_URL = "http://localhost/logos/venues/" + VENUE_ID + "/logo-new.webp";

    @Mock
    private VenuesRepository venuesRepository;

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private CitiesRepository citiesRepository;

    @Mock
    private EventsRepository eventsRepository;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private VenuesMapper venuesMapper;

    @Mock
    private UsersService usersService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private WebpConverterService webpConverterService;

    @InjectMocks
    private VenuesServiceImpl venuesService;

    @Test
    void updateLogoAsAdminConvertsUploadsAndSavesNewUrl() {
        Venues venue = venue(null);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512)).thenReturn(WEBP_BYTES);
        when(objectStorageService.uploadVenueLogo(VENUE_ID, WEBP_BYTES)).thenReturn(NEW_LOGO_URL);
        when(venuesRepository.save(venue)).thenReturn(venue);

        Venues result = venuesService.updateLogoAsAdmin(VENUE_ID, logoFile(JPEG_BYTES));

        assertThat(result).isSameAs(venue);
        assertThat(venue.getLogoUrl()).isEqualTo(NEW_LOGO_URL);
        verify(objectStorageService, never()).deleteVenueLogo(anyString());
    }

    @Test
    void updateLogoAsAdminDeletesPreviousLogoOnlyAfterSavingNewOne() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512)).thenReturn(WEBP_BYTES);
        when(objectStorageService.uploadVenueLogo(VENUE_ID, WEBP_BYTES)).thenReturn(NEW_LOGO_URL);
        when(venuesRepository.save(venue)).thenReturn(venue);

        venuesService.updateLogoAsAdmin(VENUE_ID, logoFile(JPEG_BYTES));

        assertThat(venue.getLogoUrl()).isEqualTo(NEW_LOGO_URL);
        InOrder inOrder = inOrder(objectStorageService, venuesRepository);
        inOrder.verify(objectStorageService).uploadVenueLogo(VENUE_ID, WEBP_BYTES);
        inOrder.verify(venuesRepository).save(venue);
        inOrder.verify(objectStorageService).deleteVenueLogo(OLD_LOGO_URL);
    }

    @Test
    void updateLogoAsAdminKeepsNewLogoWhenPreviousDeleteFails() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512)).thenReturn(WEBP_BYTES);
        when(objectStorageService.uploadVenueLogo(VENUE_ID, WEBP_BYTES)).thenReturn(NEW_LOGO_URL);
        when(venuesRepository.save(venue)).thenReturn(venue);
        doThrow(new MediaStorageException("boom")).when(objectStorageService).deleteVenueLogo(OLD_LOGO_URL);

        Venues result = venuesService.updateLogoAsAdmin(VENUE_ID, logoFile(JPEG_BYTES));

        assertThat(result.getLogoUrl()).isEqualTo(NEW_LOGO_URL);
    }

    @Test
    void updateLogoAsAdminRejectsContentThatIsNotAnImage() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));

        assertThatThrownBy(() -> venuesService.updateLogoAsAdmin(VENUE_ID, logoFile("GIF89a".getBytes())))
                .isInstanceOf(InvalidMediaTypeException.class);

        assertThat(venue.getLogoUrl()).isEqualTo(OLD_LOGO_URL);
        verifyNoInteractions(webpConverterService, objectStorageService);
        verify(venuesRepository, never()).save(any());
    }

    @Test
    void updateLogoAsAdminDoesNotUploadWhenConversionFails() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512))
                .thenThrow(new MediaStorageException("cwebp exited with code 1"));

        assertThatThrownBy(() -> venuesService.updateLogoAsAdmin(VENUE_ID, logoFile(JPEG_BYTES)))
                .isInstanceOf(MediaStorageException.class);

        assertThat(venue.getLogoUrl()).isEqualTo(OLD_LOGO_URL);
        verifyNoInteractions(objectStorageService);
        verify(venuesRepository, never()).save(any());
    }

    @Test
    void updateLogoAsAdminRejectsMissingVenue() {
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venuesService.updateLogoAsAdmin(VENUE_ID, logoFile(JPEG_BYTES)))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(webpConverterService, objectStorageService);
    }

    @Test
    void deleteLogoAsAdminClearsUrlAndDeletesStoredLogo() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(venuesRepository.save(venue)).thenReturn(venue);

        Venues result = venuesService.deleteLogoAsAdmin(VENUE_ID);

        assertThat(result.getLogoUrl()).isNull();
        InOrder inOrder = inOrder(venuesRepository, objectStorageService);
        inOrder.verify(venuesRepository).save(venue);
        inOrder.verify(objectStorageService).deleteVenueLogo(OLD_LOGO_URL);
    }

    @Test
    void deleteLogoAsAdminSkipsStorageWhenVenueHasNoLogo() {
        Venues venue = venue(null);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(venuesRepository.save(venue)).thenReturn(venue);

        venuesService.deleteLogoAsAdmin(VENUE_ID);

        verifyNoInteractions(objectStorageService);
    }

    @Test
    void deleteLogoAsAdminSucceedsWhenStorageDeleteFails() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(venuesRepository.save(venue)).thenReturn(venue);
        doThrow(new MediaStorageException("boom")).when(objectStorageService).deleteVenueLogo(OLD_LOGO_URL);

        Venues result = venuesService.deleteLogoAsAdmin(VENUE_ID);

        assertThat(result.getLogoUrl()).isNull();
    }

    @Test
    void deleteVenueRemovesStoredLogo() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventsRepository.existsByVenueIdAndStatusNot(VENUE_ID, EventStatus.CANCELLED)).thenReturn(false);

        venuesService.deleteVenue(VENUE_ID);

        InOrder inOrder = inOrder(venuesRepository, objectStorageService);
        inOrder.verify(venuesRepository).delete(venue);
        inOrder.verify(objectStorageService).deleteVenueLogo(OLD_LOGO_URL);
    }

    @Test
    void deleteVenueWithActiveEventsKeepsVenueAndLogo() {
        Venues venue = venue(OLD_LOGO_URL);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventsRepository.existsByVenueIdAndStatusNot(VENUE_ID, EventStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> venuesService.deleteVenue(VENUE_ID))
                .isInstanceOf(VenueHasActiveEventsException.class);

        verify(venuesRepository, never()).delete(any(Venues.class));
        verify(objectStorageService, never()).deleteVenueLogo(anyString());
    }

    private Venues venue(String logoUrl) {
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        venue.setLogoUrl(logoUrl);
        return venue;
    }

    private MockMultipartFile logoFile(byte[] content) {
        return new MockMultipartFile("logo", "logo.jpg", "image/jpeg", content);
    }
}
