package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrganizerLogoListenerTest {

    private static final String LOGO_URL = "http://localhost/logos/organizers/x/logo-1.webp";

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private OrganizerLogoListener listener;

    @Test
    void deletesTheLogoFromTheBucket() {
        listener.onOrganizerLogoDeleted(new OrganizerLogoDeletedEvent(LOGO_URL));

        verify(objectStorageService).deleteOrganizerLogo(LOGO_URL);
    }

    /** Ya hizo commit: un fallo de S3 solo deja un archivo huérfano, no se propaga. */
    @Test
    void swallowsStorageFailures() {
        doThrow(new MediaStorageException("Failed to delete stored flyer", new RuntimeException("boom")))
                .when(objectStorageService).deleteOrganizerLogo(LOGO_URL);

        assertThatCode(() -> listener.onOrganizerLogoDeleted(new OrganizerLogoDeletedEvent(LOGO_URL)))
                .doesNotThrowAnyException();
    }
}
