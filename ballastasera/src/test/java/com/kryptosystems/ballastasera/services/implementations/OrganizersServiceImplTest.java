package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.events.OrganizerClaimedEvent;
import com.kryptosystems.ballastasera.events.OrganizerLogoDeletedEvent;
import com.kryptosystems.ballastasera.events.OrganizerUnclaimedEvent;
import com.kryptosystems.ballastasera.exceptions.OrganizerAlreadyClaimedException;
import com.kryptosystems.ballastasera.exceptions.OrganizerRequestConflictException;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizerRequestsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizersServiceImplTest {

    private static final UUID ORGANIZER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private OrganizerRequestsRepository organizerRequestsRepository;

    @Mock
    private EventsRepository eventsRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private UsersService usersService;

    @Mock
    private OrganizerMapper organizerMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private WebpConverterService webpConverterService;

    @InjectMocks
    private OrganizersServiceImpl organizersService;

    private static final String LOGO_URL = "http://localhost/logos/organizers/" + ORGANIZER_ID + "/logo-1.webp";
    private static final String NEW_LOGO_URL = "http://localhost/logos/organizers/" + ORGANIZER_ID + "/logo-2.webp";
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] WEBP_BYTES = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    // ---------- D7: logos ----------

    /** El dueño no pone logoUrl en texto: usa el método del mapper que lo ignora. */
    @Test
    void updateByOwnerUsesTheMapperMethodWithoutLogo() {
        Organizers organizer = organizer(user(OWNER_ID));
        OrganizerUpdateDto dto = new OrganizerUpdateDto();
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        organizersService.update(ORGANIZER_ID, OWNER_ID, dto);

        verify(organizerMapper).updateOwnedOrganizerFromDto(dto, organizer);
        verify(organizerMapper, never()).updateOrganizerFromDto(any(), any());
    }

    @Test
    void updateAsAdminKeepsAcceptingTheLogoUrl() {
        Organizers organizer = organizer(null);
        OrganizerUpdateDto dto = new OrganizerUpdateDto();
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        organizersService.updateAsAdmin(ORGANIZER_ID, dto);

        verify(organizerMapper).updateOrganizerFromDto(dto, organizer);
        verify(organizerMapper, never()).updateOwnedOrganizerFromDto(any(), any());
    }

    /** El anterior se borra del bucket después del commit (evento), no en el momento. */
    @Test
    void updateLogoUploadsTheNewLogoAndDiscardsThePreviousOne() {
        Organizers organizer = organizer(user(OWNER_ID));
        organizer.setLogoUrl(LOGO_URL);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512)).thenReturn(WEBP_BYTES);
        when(objectStorageService.uploadOrganizerLogo(ORGANIZER_ID, WEBP_BYTES)).thenReturn(NEW_LOGO_URL);
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        Organizers result = organizersService.updateLogo(ORGANIZER_ID, OWNER_ID, jpeg());

        assertThat(result.getLogoUrl()).isEqualTo(NEW_LOGO_URL);
        assertThat(publishedLogoDeletion()).isEqualTo(LOGO_URL);
        verify(objectStorageService, never()).deleteOrganizerLogo(any());
    }

    @Test
    void updateLogoWithoutPreviousLogoDiscardsNothing() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512)).thenReturn(WEBP_BYTES);
        when(objectStorageService.uploadOrganizerLogo(ORGANIZER_ID, WEBP_BYTES)).thenReturn(NEW_LOGO_URL);
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        organizersService.updateLogo(ORGANIZER_ID, OWNER_ID, jpeg());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateLogoByNonOwnerIsRejected() {
        Organizers organizer = organizer(user(OWNER_ID));
        organizer.setLogoUrl(LOGO_URL);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.updateLogo(ORGANIZER_ID, OTHER_USER_ID, jpeg()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(objectStorageService, eventPublisher);
        assertThat(organizer.getLogoUrl()).isEqualTo(LOGO_URL);
    }

    @Test
    void deleteDiscardsTheLogo() {
        Organizers organizer = organizer(user(OWNER_ID));
        organizer.setLogoUrl(LOGO_URL);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.existsByOrganizerIdAndUserIdAndTypeAndStatus(
                ORGANIZER_ID, OWNER_ID, OrganizerRequestType.NEW, OrganizerRequestStatus.APPROVED)).thenReturn(true);

        organizersService.delete(ORGANIZER_ID, OWNER_ID);

        assertThat(publishedLogoDeletion()).isEqualTo(LOGO_URL);
    }

    @Test
    void deleteAsAdminDiscardsTheLogo() {
        Organizers organizer = organizer(null);
        organizer.setLogoUrl(LOGO_URL);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        organizersService.deleteAsAdmin(ORGANIZER_ID);

        assertThat(publishedLogoDeletion()).isEqualTo(LOGO_URL);
    }

    // ---------- D13: eventos futuros al borrar ----------

    /** Antes del DELETE: después organizer_id ya es null (ON DELETE SET NULL) y los UPDATE no encontrarían nada. */
    @Test
    void deleteRetiresUpcomingEventsAndSeriesBeforeDeleting() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.existsByOrganizerIdAndUserIdAndTypeAndStatus(
                ORGANIZER_ID, OWNER_ID, OrganizerRequestType.NEW, OrganizerRequestStatus.APPROVED)).thenReturn(true);

        organizersService.delete(ORGANIZER_ID, OWNER_ID);

        InOrder inOrder = inOrder(eventsRepository, eventSeriesRepository, organizersRepository);
        inOrder.verify(eventsRepository).cancelUpcomingByOrganizerId(eq(ORGANIZER_ID), any(), eq(EventStatus.CANCELLED));
        inOrder.verify(eventSeriesRepository).deactivateByOrganizerId(eq(ORGANIZER_ID), any());
        inOrder.verify(organizersRepository).delete(organizer);
    }

    @Test
    void deleteAsAdminRetiresUpcomingEventsAndSeriesBeforeDeleting() {
        Organizers organizer = organizer(null);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        organizersService.deleteAsAdmin(ORGANIZER_ID);

        InOrder inOrder = inOrder(eventsRepository, eventSeriesRepository, organizersRepository);
        inOrder.verify(eventsRepository).cancelUpcomingByOrganizerId(eq(ORGANIZER_ID), any(), eq(EventStatus.CANCELLED));
        inOrder.verify(eventSeriesRepository).deactivateByOrganizerId(eq(ORGANIZER_ID), any());
        inOrder.verify(organizersRepository).delete(organizer);
    }

    @Test
    void deleteRejectedByD13RetiresNothing() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.delete(ORGANIZER_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(eventsRepository, eventSeriesRepository);
    }

    // ---------- D13: release y unclaim ----------

    /** Le llegó por CLAIM: lo suelta sin borrar nada y baja a USER si no le quedan. Sin email. */
    @Test
    void releaseRemovesTheOwnerAndDemotesTheUser() {
        Organizers organizer = organizer(user(OWNER_ID));
        organizer.setLogoUrl(LOGO_URL);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        organizersService.release(ORGANIZER_ID, OWNER_ID);

        assertThat(organizer.getUser()).isNull();
        assertThat(organizer.isClaimed()).isFalse();
        assertThat(organizer.getLogoUrl()).isEqualTo(LOGO_URL);
        verify(usersService).demoteToUser(OWNER_ID);
        verify(organizersRepository, never()).delete(any());
        verifyNoInteractions(eventPublisher, eventsRepository);
    }

    @Test
    void releaseOfAnOrganizerCreatedWithANewIsRejected() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.existsByOrganizerIdAndUserIdAndTypeAndStatus(
                ORGANIZER_ID, OWNER_ID, OrganizerRequestType.NEW, OrganizerRequestStatus.APPROVED)).thenReturn(true);

        assertThatThrownBy(() -> organizersService.release(ORGANIZER_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(organizer.getUser()).isNotNull();
        verifyNoInteractions(usersService);
    }

    @Test
    void releaseByNonOwnerIsRejected() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.release(ORGANIZER_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(organizerRequestsRepository, usersService);
    }

    @Test
    void unclaimAsAdminRemovesTheOwnerAndPublishesTheEmail() {
        Users owner = user(OWNER_ID);
        owner.setEmail("ana@example.com");
        Organizers organizer = organizer(owner);
        organizer.setName("Salsa Loca Milano");
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        organizersService.unclaimAsAdmin(ORGANIZER_ID);

        assertThat(organizer.getUser()).isNull();
        assertThat(organizer.isClaimed()).isFalse();
        verify(usersService).demoteToUser(OWNER_ID);
        ArgumentCaptor<OrganizerUnclaimedEvent> captor = ArgumentCaptor.forClass(OrganizerUnclaimedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getToEmail()).isEqualTo("ana@example.com");
        assertThat(captor.getValue().getOrganizerName()).isEqualTo("Salsa Loca Milano");
    }

    @Test
    void unclaimAsAdminOfAnOrganizerWithoutOwnerIsAConflict() {
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer(null)));

        assertThatThrownBy(() -> organizersService.unclaimAsAdmin(ORGANIZER_ID))
                .isInstanceOf(OrganizerRequestConflictException.class)
                .extracting(ex -> ((OrganizerRequestConflictException) ex).getCode())
                .isEqualTo(OrganizerRequestConflictException.ORGANIZER_NOT_CLAIMED);
        verifyNoInteractions(eventPublisher, usersService);
    }

    @Test
    void unclaimAsAdminOfAnOrganizerCreatedWithANewIsAConflict() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.existsByOrganizerIdAndUserIdAndTypeAndStatus(
                ORGANIZER_ID, OWNER_ID, OrganizerRequestType.NEW, OrganizerRequestStatus.APPROVED)).thenReturn(true);

        assertThatThrownBy(() -> organizersService.unclaimAsAdmin(ORGANIZER_ID))
                .isInstanceOf(OrganizerRequestConflictException.class)
                .extracting(ex -> ((OrganizerRequestConflictException) ex).getCode())
                .isEqualTo(OrganizerRequestConflictException.ORGANIZER_CREATED_BY_OWNER);
        assertThat(organizer.getUser()).isNotNull();
        verifyNoInteractions(eventPublisher, usersService);
    }

    // ---------- claim / claimable ----------

    /** Un organizador creado por su dueño y verificado sigue con claimed = false: también cuenta user != null. */
    @Test
    void claimOfAnOrganizerThatAlreadyHasAnOwnerIsRejected() {
        Organizers organizer = organizer(user(OWNER_ID));
        organizer.setClaimed(false);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.claim(ORGANIZER_ID, OTHER_USER_ID))
                .isInstanceOf(OrganizerAlreadyClaimedException.class);
        verifyNoInteractions(usersService, eventPublisher);
    }

    @Test
    void claimAssignsTheOwnerPromotesTheUserAndPublishesTheEmail() {
        Organizers organizer = organizer(null);
        Users user = user(OTHER_USER_ID);
        user.setEmail("marco@example.com");
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(usersService.findById(OTHER_USER_ID)).thenReturn(user);
        when(organizersRepository.save(organizer)).thenReturn(organizer);

        organizersService.claim(ORGANIZER_ID, OTHER_USER_ID);

        assertThat(organizer.getUser()).isSameAs(user);
        assertThat(organizer.isClaimed()).isTrue();
        verify(usersService).promoteToOrganizer(OTHER_USER_ID);
        verify(eventPublisher).publishEvent(any(OrganizerClaimedEvent.class));
    }

    @Test
    void findClaimableTrimsTheName() {
        organizersService.findClaimable("  sal  ", 5);

        verify(organizersRepository).findByIsVerifiedTrueAndClaimedFalseAndUserIsNullAndNameContainingIgnoreCaseOrderByNameAsc(
                eq("sal"), any(Limit.class));
    }

    private String publishedLogoDeletion() {
        ArgumentCaptor<OrganizerLogoDeletedEvent> captor = ArgumentCaptor.forClass(OrganizerLogoDeletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue().getLogoUrl();
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("logo", "logo.jpg", "image/jpeg", JPEG_BYTES);
    }

    /** El rol se baja después de borrar: si no, countByUserId todavía vería el organizador. */
    @Test
    void deleteRemovesOrganizerAndThenDemotesOwner() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.existsByOrganizerIdAndUserIdAndTypeAndStatus(
                ORGANIZER_ID, OWNER_ID, OrganizerRequestType.NEW, OrganizerRequestStatus.APPROVED)).thenReturn(true);

        organizersService.delete(ORGANIZER_ID, OWNER_ID);

        InOrder inOrder = inOrder(organizersRepository, usersService);
        inOrder.verify(organizersRepository).delete(organizer);
        inOrder.verify(usersService).demoteToUser(OWNER_ID);
    }

    @Test
    void deleteByNonOwnerIsRejectedAndDeletesNothing() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.delete(ORGANIZER_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(organizersRepository, never()).delete(any());
        verifyNoInteractions(usersService);
    }

    /** D13: llegó por CLAIM, no hay NEW APPROVED de ese usuario (false por defecto en Mockito). */
    @Test
    void deleteOfOrganizerNotCreatedByOwnerIsRejectedAndDeletesNothing() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.delete(ORGANIZER_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(organizersRepository, never()).delete(any());
        verifyNoInteractions(usersService);
    }

    /** Sin CLAIM PENDING: Mockito devuelve una lista vacía por defecto, no hace falta stub. */
    @Test
    void deleteAsAdminRemovesOrganizerAndThenDemotesOwner() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        organizersService.deleteAsAdmin(ORGANIZER_ID);

        InOrder inOrder = inOrder(organizersRepository, usersService);
        inOrder.verify(organizersRepository).delete(organizer);
        inOrder.verify(usersService).demoteToUser(OWNER_ID);
    }

    /** Problema 9 del plan: las CLAIM PENDING pasan a CANCELLED. Si no, ON DELETE SET NULL las dejaría
     * PENDING sin organizer_id y la base de datos rechazaría el borrado (chk_org_request_claim). */
    @Test
    void deleteAsAdminCancelsPendingClaimsOfUnclaimedOrganizer() {
        Organizers organizer = organizer(null);
        OrganizerRequests anaClaim = pendingClaim(organizer);
        OrganizerRequests marcoClaim = pendingClaim(organizer);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.findByOrganizerIdAndTypeAndStatus(
                ORGANIZER_ID, OrganizerRequestType.CLAIM, OrganizerRequestStatus.PENDING))
                .thenReturn(List.of(anaClaim, marcoClaim));

        organizersService.deleteAsAdmin(ORGANIZER_ID);

        assertThat(List.of(anaClaim, marcoClaim)).allSatisfy(claim -> {
            assertThat(claim.getStatus()).isEqualTo(OrganizerRequestStatus.CANCELLED);
            assertThat(claim.getRejectionMessage()).isEqualTo("La pagina è stata rimossa.");
        });
        verify(organizersRepository).delete(organizer);
        verifyNoInteractions(usersService);
    }

    /** Antes deleteById ignoraba un id inexistente; ahora hay que cargar el organizador para saber su dueño. */
    @Test
    void deleteAsAdminThrowsWhenOrganizerDoesNotExist() {
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizersService.deleteAsAdmin(ORGANIZER_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verify(organizersRepository, never()).delete(any());
    }

    private static Organizers organizer(Users owner) {
        Organizers organizer = new Organizers();
        organizer.setId(ORGANIZER_ID);
        organizer.setUser(owner);
        organizer.setClaimed(owner != null);
        return organizer;
    }

    private static Users user(UUID id) {
        Users user = new Users();
        user.setId(id);
        return user;
    }

    /** status = PENDING por defecto en la entidad. */
    private static OrganizerRequests pendingClaim(Organizers organizer) {
        OrganizerRequests claim = new OrganizerRequests();
        claim.setType(OrganizerRequestType.CLAIM);
        claim.setOrganizer(organizer);
        return claim;
    }
}
