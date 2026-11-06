package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;
import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.enums.OrganizerType;
import com.kryptosystems.ballastasera.events.OrganizerLogoDeletedEvent;
import com.kryptosystems.ballastasera.events.OrganizerRequestReviewedEvent;
import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;
import com.kryptosystems.ballastasera.exceptions.OrganizerAlreadyClaimedException;
import com.kryptosystems.ballastasera.exceptions.OrganizerRequestConflictException;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestApproveDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestRejectDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.OrganizerRequestMapper;
import com.kryptosystems.ballastasera.repositories.OrganizerRequestsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizerRequestsServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final UUID REQUEST_ID = UUID.fromString("ab000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_REQUEST_ID = UUID.fromString("ab000000-0000-0000-0000-000000000002");
    private static final UUID ORGANIZER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final String LOGO_URL = "http://localhost/logos/organizers/" + REQUEST_ID + "/logo-1.webp";
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] WEBP_BYTES = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Mock
    private OrganizerRequestsRepository organizerRequestsRepository;

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private VenuesRepository venuesRepository;

    @Mock
    private OrganizersService organizersService;

    @Mock
    private UsersService usersService;

    @Mock
    private CitiesService citiesService;

    @Mock
    private OrganizerRequestMapper organizerRequestMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private WebpConverterService webpConverterService;

    @InjectMocks
    private OrganizerRequestsServiceImpl organizerRequestsService;

    // ---------- create ----------

    @Test
    void createNewSavesPendingRequestOfTheUserWithNormalizedInstagram() {
        Users user = user(USER_ID);
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.NEW);
        OrganizerRequests mapped = mappedRequest(OrganizerRequestType.NEW);
        mapped.setInstagram("@salsa.loca");
        when(usersService.findById(USER_ID)).thenReturn(user);
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mapped);
        when(organizerRequestsRepository.saveAndFlush(mapped)).thenReturn(mapped);

        OrganizerRequests result = organizerRequestsService.create(USER_ID, dto);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getStatus()).isEqualTo(OrganizerRequestStatus.PENDING);
        assertThat(result.getInstagram()).isEqualTo("salsa.loca");
        assertThat(result.getTermsAcceptedAt()).isNotNull();
        assertThat(result.getLogoUrl()).isNull();
    }

    /** D6: en una CLAIM solo cuentan los datos de prueba; el perfil y el local son los del organizador pedido. */
    @Test
    void createClaimLinksTargetAndClearsProfileFields() {
        Organizers target = organizer(null, OrganizerType.SCHOOL);
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.CLAIM);
        dto.setTargetOrganizerId(ORGANIZER_ID);
        OrganizerRequests mapped = mappedRequest(OrganizerRequestType.CLAIM);
        mapped.setName("Nome inventato");
        mapped.setDescription("Descrizione");
        mapped.setVenueAddress("Via Roma 1");
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mapped);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(target));
        when(organizerRequestsRepository.saveAndFlush(mapped)).thenReturn(mapped);

        OrganizerRequests result = organizerRequestsService.create(USER_ID, dto);

        assertThat(result.getOrganizer()).isSameAs(target);
        assertThat(result.getName()).isNull();
        assertThat(result.getDescription()).isNull();
        assertThat(result.getVenueAddress()).isNull();
        verifyNoInteractions(citiesService);
    }

    @Test
    void createRejectsBannedUser() {
        Users user = user(USER_ID);
        user.setBanned(true);
        when(usersService.findById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> organizerRequestsService.create(USER_ID, createDto(OrganizerRequestType.NEW)))
                .isInstanceOf(AccessDeniedException.class);

        verify(organizerRequestsRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUserWithAPendingRequest() {
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestsRepository.existsByUserIdAndStatus(USER_ID, OrganizerRequestStatus.PENDING)).thenReturn(true);

        assertConflict(() -> organizerRequestsService.create(USER_ID, createDto(OrganizerRequestType.NEW)),
                OrganizerRequestConflictException.PENDING_REQUEST_EXISTS);
        verify(organizerRequestsRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUserWhoAlreadyOwnsAnOrganizer() {
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizersRepository.countByUserId(USER_ID)).thenReturn(1L);

        assertConflict(() -> organizerRequestsService.create(USER_ID, createDto(OrganizerRequestType.NEW)),
                OrganizerRequestConflictException.ORGANIZER_LIMIT_REACHED);
        verify(organizerRequestsRepository, never()).saveAndFlush(any());
    }

    @Test
    void createClaimOfAPageThatAlreadyHasAnOwnerIsRejected() {
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.CLAIM);
        dto.setTargetOrganizerId(ORGANIZER_ID);
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mappedRequest(OrganizerRequestType.CLAIM));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer(user(OTHER_USER_ID), OrganizerType.PERSON)));

        assertThatThrownBy(() -> organizerRequestsService.create(USER_ID, dto))
                .isInstanceOf(OrganizerAlreadyClaimedException.class);
        verify(organizerRequestsRepository, never()).saveAndFlush(any());
    }

    /** Sin verificar cuenta como inexistente: mismo 404, para no revelar borradores. */
    @Test
    void createClaimOfAnUnverifiedPageIsNotFound() {
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.CLAIM);
        dto.setTargetOrganizerId(ORGANIZER_ID);
        Organizers unverified = organizer(null, OrganizerType.PERSON);
        unverified.setVerified(false);
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mappedRequest(OrganizerRequestType.CLAIM));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> organizerRequestsService.create(USER_ID, dto))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createNewWithAVenueCityThatDoesNotExistIsNotFound() {
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.NEW);
        dto.setVenueCityId(99L);
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mappedRequest(OrganizerRequestType.NEW));
        when(citiesService.findById(99L)).thenThrow(new EntityNotFoundException("City not found with id 99"));

        assertThatThrownBy(() -> organizerRequestsService.create(USER_ID, dto))
                .isInstanceOf(EntityNotFoundException.class);
        verify(organizerRequestsRepository, never()).saveAndFlush(any());
    }

    @Test
    void createNewKeepsTheVenueCity() {
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.NEW);
        dto.setVenueCityId(1L);
        OrganizerRequests mapped = mappedRequest(OrganizerRequestType.NEW);
        Cities milano = new Cities();
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mapped);
        when(citiesService.findById(1L)).thenReturn(milano);
        when(organizerRequestsRepository.saveAndFlush(mapped)).thenReturn(mapped);

        assertThat(organizerRequestsService.create(USER_ID, dto).getVenueCity()).isSameAs(milano);
    }

    /** Dos create a la vez: el índice parcial de una sola PENDING salta en el INSERT y se traduce al mismo 409. */
    @Test
    void createTranslatesTheOnePendingIndexViolationToConflict() {
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.NEW);
        OrganizerRequests mapped = mappedRequest(OrganizerRequestType.NEW);
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mapped);
        when(organizerRequestsRepository.saveAndFlush(mapped)).thenThrow(new DataIntegrityViolationException("insert failed",
                new RuntimeException("duplicate key value violates unique constraint \"uq_org_requests_one_pending\"")));

        assertConflict(() -> organizerRequestsService.create(USER_ID, dto),
                OrganizerRequestConflictException.PENDING_REQUEST_EXISTS);
    }

    @Test
    void createRethrowsOtherIntegrityViolations() {
        OrganizerRequestCreateDto dto = createDto(OrganizerRequestType.NEW);
        OrganizerRequests mapped = mappedRequest(OrganizerRequestType.NEW);
        DataIntegrityViolationException other = new DataIntegrityViolationException("insert failed",
                new RuntimeException("violates check constraint \"chk_org_request_claim\""));
        when(usersService.findById(USER_ID)).thenReturn(user(USER_ID));
        when(organizerRequestMapper.toOrganizerRequestEntity(dto)).thenReturn(mapped);
        when(organizerRequestsRepository.saveAndFlush(mapped)).thenThrow(other);

        assertThatThrownBy(() -> organizerRequestsService.create(USER_ID, dto)).isSameAs(other);
    }

    // ---------- cancel ----------

    @Test
    void cancelMovesPendingToCancelledAndAcknowledgesIt() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        when(organizerRequestsRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        organizerRequestsService.cancel(REQUEST_ID, USER_ID);

        assertThat(request.getStatus()).isEqualTo(OrganizerRequestStatus.CANCELLED);
        assertThat(request.getAcknowledgedAt()).isNotNull();
        verifyNoInteractions(eventPublisher);
    }

    /** D7: el logo de una solicitud cancelada no se va a usar: se borra después del commit. */
    @Test
    void cancelWithLogoPublishesTheLogoDeletionAndClearsIt() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setLogoUrl(LOGO_URL);
        when(organizerRequestsRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        organizerRequestsService.cancel(REQUEST_ID, USER_ID);

        assertThat(publishedLogoDeletion()).isEqualTo(LOGO_URL);
        assertThat(request.getLogoUrl()).isNull();
    }

    @Test
    void cancelByAnotherUserIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(OTHER_USER_ID));
        when(organizerRequestsRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> organizerRequestsService.cancel(REQUEST_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(request.getStatus()).isEqualTo(OrganizerRequestStatus.PENDING);
    }

    @Test
    void cancelOfARequestThatIsNoLongerPendingIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setStatus(OrganizerRequestStatus.APPROVED);
        when(organizerRequestsRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.cancel(REQUEST_ID, USER_ID),
                OrganizerRequestConflictException.REQUEST_NOT_PENDING);
    }

    // ---------- updateLogo ----------

    @Test
    void updateLogoUploadsTheLogoOfANewPendingRequest() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(webpConverterService.convertToWebp(JPEG_BYTES, 512)).thenReturn(WEBP_BYTES);
        when(objectStorageService.uploadOrganizerLogo(REQUEST_ID, WEBP_BYTES)).thenReturn(LOGO_URL);

        OrganizerRequests result = organizerRequestsService.updateLogo(REQUEST_ID, USER_ID, jpeg());

        assertThat(result.getLogoUrl()).isEqualTo(LOGO_URL);
    }

    /** D7: el logo se sube una sola vez; mientras está PENDING no se cambia. */
    @Test
    void updateLogoRejectsASecondUpload() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setLogoUrl(LOGO_URL);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.updateLogo(REQUEST_ID, USER_ID, jpeg()),
                OrganizerRequestConflictException.LOGO_NOT_ALLOWED);
        verifyNoInteractions(objectStorageService, webpConverterService);
        assertThat(request.getLogoUrl()).isEqualTo(LOGO_URL);
    }

    @Test
    void updateLogoRejectsAClaim() {
        OrganizerRequests request = request(OrganizerRequestType.CLAIM, user(USER_ID));
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.updateLogo(REQUEST_ID, USER_ID, jpeg()),
                OrganizerRequestConflictException.LOGO_NOT_ALLOWED);
        verifyNoInteractions(objectStorageService, webpConverterService);
    }

    @Test
    void updateLogoRejectsARequestThatIsNoLongerPending() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setStatus(OrganizerRequestStatus.REJECTED);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.updateLogo(REQUEST_ID, USER_ID, jpeg()),
                OrganizerRequestConflictException.REQUEST_NOT_PENDING);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void updateLogoByAnotherUserIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(OTHER_USER_ID));
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> organizerRequestsService.updateLogo(REQUEST_ID, USER_ID, jpeg()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void updateLogoRejectsAFileThatIsNotAnImage() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        MockMultipartFile pdf = new MockMultipartFile("logo", "logo.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatThrownBy(() -> organizerRequestsService.updateLogo(REQUEST_ID, USER_ID, pdf))
                .isInstanceOf(InvalidMediaTypeException.class);
        verifyNoInteractions(objectStorageService);
        assertThat(request.getLogoUrl()).isNull();
    }

    // ---------- acknowledge ----------

    @Test
    void acknowledgeOfAPendingRequestIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.acknowledge(REQUEST_ID, USER_ID),
                OrganizerRequestConflictException.REQUEST_STILL_PENDING);
    }

    @Test
    void acknowledgeKeepsTheFirstDate() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setStatus(OrganizerRequestStatus.REJECTED);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        var first = organizerRequestsService.acknowledge(REQUEST_ID, USER_ID).getAcknowledgedAt();
        var second = organizerRequestsService.acknowledge(REQUEST_ID, USER_ID).getAcknowledgedAt();

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void acknowledgeByAnotherUserIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(OTHER_USER_ID));
        request.setStatus(OrganizerRequestStatus.APPROVED);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> organizerRequestsService.acknowledge(REQUEST_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(request.getAcknowledgedAt()).isNull();
    }

    // ---------- reject ----------

    @Test
    void rejectStoresTheReasonTrimsTheMessageAndPublishesTheEmail() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        Users admin = user(ADMIN_ID);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(admin);

        organizerRequestsService.reject(REQUEST_ID, ADMIN_ID, rejectDto(OrganizerRejectReason.OTHER, "  Account sospeso.  "));

        assertThat(request.getStatus()).isEqualTo(OrganizerRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo(OrganizerRejectReason.OTHER);
        assertThat(request.getRejectionMessage()).isEqualTo("Account sospeso.");
        assertThat(request.getReviewedBy()).isSameAs(admin);
        assertThat(request.getReviewedAt()).isNotNull();
        OrganizerRequestReviewedEvent event = publishedEvent(OrganizerRequestReviewedEvent.class);
        assertThat(event.getStatus()).isEqualTo(OrganizerRequestStatus.REJECTED);
        assertThat(event.getOrganizerName()).isEqualTo("Salsa Loca Milano");
        assertThat(event.getRejectionMessage()).isEqualTo("Account sospeso.");
    }

    @Test
    void rejectStoresABlankMessageAsNull() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(user(ADMIN_ID));

        organizerRequestsService.reject(REQUEST_ID, ADMIN_ID, rejectDto(OrganizerRejectReason.INCOMPLETE_DATA, "   "));

        assertThat(request.getRejectionMessage()).isNull();
    }

    /** D7: el logo de una solicitud rechazada se borra; «Modifica e reinvia» lo vuelve a subir. */
    @Test
    void rejectWithLogoPublishesTheLogoDeletionAndClearsIt() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setLogoUrl(LOGO_URL);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(user(ADMIN_ID));

        organizerRequestsService.reject(REQUEST_ID, ADMIN_ID, rejectDto(OrganizerRejectReason.INCOMPLETE_DATA, null));

        assertThat(publishedEvent(OrganizerLogoDeletedEvent.class).getLogoUrl()).isEqualTo(LOGO_URL);
        assertThat(request.getLogoUrl()).isNull();
    }

    @Test
    void rejectOfARequestThatIsNoLongerPendingIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setStatus(OrganizerRequestStatus.CANCELLED);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.reject(REQUEST_ID, ADMIN_ID, rejectDto(OrganizerRejectReason.OTHER, "x")),
                OrganizerRequestConflictException.REQUEST_NOT_PENDING);
        verifyNoInteractions(eventPublisher);
    }

    // ---------- approve ----------

    @Test
    void approveNewCreatesAVerifiedOrganizerOwnedByTheUserAndPromotesIt() {
        Users user = user(USER_ID);
        user.setEmail("ana@example.com");
        OrganizerRequests request = request(OrganizerRequestType.NEW, user);
        request.setLogoUrl(LOGO_URL);
        Organizers mapped = organizer(null, OrganizerType.PERSON);
        mapped.setVerified(false);
        mapped.setLogoUrl(LOGO_URL);
        Users admin = user(ADMIN_ID);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(admin);
        when(organizerRequestMapper.toOrganizerEntity(request)).thenReturn(mapped);
        when(organizersRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(organizersRepository.save(mapped)).thenReturn(mapped);

        OrganizerRequests result = organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null);

        assertThat(mapped.isVerified()).isTrue();
        // D15: la NEW deja claimed = false (lo creó su dueño); true solo por una CLAIM
        assertThat(mapped.isClaimed()).isFalse();
        assertThat(mapped.getUser()).isSameAs(user);
        assertThat(mapped.getSlug()).isEqualTo("salsa-loca-milano");
        assertThat(mapped.getLogoUrl()).isEqualTo(LOGO_URL);
        assertThat(result.getOrganizer()).isSameAs(mapped);
        assertThat(result.getStatus()).isEqualTo(OrganizerRequestStatus.APPROVED);
        assertThat(result.getReviewedBy()).isSameAs(admin);
        assertThat(result.getReviewedAt()).isNotNull();
        verify(usersService).promoteToOrganizer(USER_ID);
        OrganizerRequestReviewedEvent event = publishedEvent(OrganizerRequestReviewedEvent.class);
        assertThat(event.getStatus()).isEqualTo(OrganizerRequestStatus.APPROVED);
        assertThat(event.getToEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void approveRejectsABannedUser() {
        Users user = user(USER_ID);
        user.setBanned(true);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID))
                .thenReturn(Optional.of(request(OrganizerRequestType.NEW, user)));

        assertThatThrownBy(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(organizersRepository, never()).save(any());
        verify(usersService, never()).promoteToOrganizer(any());
    }

    @Test
    void approveRejectsAUserWhoAlreadyOwnsAnOrganizer() {
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID))
                .thenReturn(Optional.of(request(OrganizerRequestType.NEW, user(USER_ID))));
        when(organizersRepository.countByUserId(USER_ID)).thenReturn(1L);

        assertConflict(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null),
                OrganizerRequestConflictException.ORGANIZER_LIMIT_REACHED);
        verify(organizersRepository, never()).save(any());
    }

    @Test
    void approveOfARequestThatIsNoLongerPendingIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        request.setStatus(OrganizerRequestStatus.REJECTED);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));

        assertConflict(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null),
                OrganizerRequestConflictException.REQUEST_NOT_PENDING);
        verifyNoInteractions(organizersService);
    }

    /** CLAIM: claim() asigna la página (y manda su email); las otras CLAIM PENDING de la misma página se
     * rechazan como DUPLICATE, cada una con su email. */
    @Test
    void approveClaimAssignsThePageAndRejectsTheCompetingClaims() {
        Organizers target = organizer(null, OrganizerType.PERSON);
        target.setName("Salsa Loca Milano");
        OrganizerRequests request = request(OrganizerRequestType.CLAIM, user(USER_ID));
        request.setOrganizer(target);
        Users marco = user(OTHER_USER_ID);
        marco.setEmail("marco@example.com");
        OrganizerRequests competing = request(OrganizerRequestType.CLAIM, marco);
        competing.setId(OTHER_REQUEST_ID);
        competing.setOrganizer(target);
        Users admin = user(ADMIN_ID);
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(admin);
        when(organizersService.claim(ORGANIZER_ID, USER_ID)).thenReturn(target);
        when(organizerRequestsRepository.findByOrganizerIdAndTypeAndStatus(
                ORGANIZER_ID, OrganizerRequestType.CLAIM, OrganizerRequestStatus.PENDING))
                .thenReturn(List.of(request, competing));

        organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null);

        assertThat(request.getStatus()).isEqualTo(OrganizerRequestStatus.APPROVED);
        assertThat(competing.getStatus()).isEqualTo(OrganizerRequestStatus.REJECTED);
        assertThat(competing.getRejectionReason()).isEqualTo(OrganizerRejectReason.DUPLICATE);
        assertThat(competing.getRejectionMessage()).isEqualTo("La pagina è stata assegnata a un altro utente.");
        assertThat(competing.getReviewedBy()).isSameAs(admin);
        OrganizerRequestReviewedEvent event = publishedEvent(OrganizerRequestReviewedEvent.class);
        assertThat(event.getToEmail()).isEqualTo("marco@example.com");
        assertThat(event.getRejectionReason()).isEqualTo(OrganizerRejectReason.DUPLICATE);
        verify(organizersRepository, never()).save(any());
    }

    /** D11: el admin acaba de crear el venue y lo pasa en el approve. */
    @Test
    void approveNewWithVenueIdLinksTheVenue() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        Organizers mapped = organizer(null, OrganizerType.CLUB);
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        stubApproveNew(request, mapped);
        when(venuesRepository.linkOrganizerIfFree(VENUE_ID, mapped)).thenReturn(1);
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(venuesRepository.findByOrganizerId(ORGANIZER_ID)).thenReturn(List.of(venue));

        OrganizerRequests result = organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, approveDto(VENUE_ID));

        assertThat(result.getVenue()).isSameAs(venue);
        assertThat(result.getStatus()).isEqualTo(OrganizerRequestStatus.APPROVED);
    }

    @Test
    void approveWithAVenueAlreadyLinkedToAnotherOrganizerIsRejected() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        Organizers mapped = organizer(null, OrganizerType.CLUB);
        stubApproveNew(request, mapped);
        when(venuesRepository.linkOrganizerIfFree(VENUE_ID, mapped)).thenReturn(0);
        when(venuesRepository.existsById(VENUE_ID)).thenReturn(true);

        assertConflict(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, approveDto(VENUE_ID)),
                OrganizerRequestConflictException.VENUE_ALREADY_LINKED);
    }

    @Test
    void approveWithAVenueThatDoesNotExistIsNotFound() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        Organizers mapped = organizer(null, OrganizerType.CLUB);
        stubApproveNew(request, mapped);
        when(venuesRepository.linkOrganizerIfFree(VENUE_ID, mapped)).thenReturn(0);
        when(venuesRepository.existsById(VENUE_ID)).thenReturn(false);

        assertThatThrownBy(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, approveDto(VENUE_ID)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void approveNewClubWithoutVenueRequiresIt() {
        OrganizerRequests request = request(OrganizerRequestType.NEW, user(USER_ID));
        Organizers mapped = organizer(null, OrganizerType.CLUB);
        stubApproveNew(request, mapped);

        assertConflict(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null),
                OrganizerRequestConflictException.VENUE_REQUIRED);
        assertThat(request.getStatus()).isEqualTo(OrganizerRequestStatus.PENDING);
    }

    /** CLAIM de una página que el admin ya enlazó con su venue: se aprueba sin body. */
    @Test
    void approveClaimOfASchoolWithItsVenueNeedsNoBody() {
        Organizers target = organizer(null, OrganizerType.SCHOOL);
        OrganizerRequests request = request(OrganizerRequestType.CLAIM, user(USER_ID));
        request.setOrganizer(target);
        stubApproveClaim(request, target);
        when(venuesRepository.findByOrganizerId(ORGANIZER_ID)).thenReturn(List.of(new Venues()));

        organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null);

        assertThat(request.getStatus()).isEqualTo(OrganizerRequestStatus.APPROVED);
    }

    @Test
    void approveClaimOfASchoolWithoutVenueRequiresIt() {
        Organizers target = organizer(null, OrganizerType.SCHOOL);
        OrganizerRequests request = request(OrganizerRequestType.CLAIM, user(USER_ID));
        request.setOrganizer(target);
        stubApproveClaim(request, target);

        assertConflict(() -> organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null),
                OrganizerRequestConflictException.VENUE_REQUIRED);
    }

    // ---------- countPreviousRejections ----------

    @Test
    void countPreviousRejectionsDoesNotCountTheRequestItself() {
        OrganizerRequests rejected = request(OrganizerRequestType.NEW, user(USER_ID));
        rejected.setStatus(OrganizerRequestStatus.REJECTED);
        OrganizerRequests pending = request(OrganizerRequestType.NEW, user(USER_ID));
        when(organizerRequestsRepository.countByUserIdAndStatus(USER_ID, OrganizerRequestStatus.REJECTED)).thenReturn(2L);

        assertThat(organizerRequestsService.countPreviousRejections(rejected)).isEqualTo(1);
        assertThat(organizerRequestsService.countPreviousRejections(pending)).isEqualTo(2);
    }

    // ---------- helpers ----------

    private void stubApproveNew(OrganizerRequests request, Organizers mapped) {
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(user(ADMIN_ID));
        when(organizerRequestMapper.toOrganizerEntity(request)).thenReturn(mapped);
        when(organizersRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(organizersRepository.save(mapped)).thenReturn(mapped);
    }

    private void stubApproveClaim(OrganizerRequests request, Organizers target) {
        when(organizerRequestsRepository.findByIdWithDetails(REQUEST_ID)).thenReturn(Optional.of(request));
        when(usersService.findById(ADMIN_ID)).thenReturn(user(ADMIN_ID));
        when(organizersService.claim(ORGANIZER_ID, USER_ID)).thenReturn(target);
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(OrganizerRequestConflictException.class)
                .extracting(ex -> ((OrganizerRequestConflictException) ex).getCode())
                .isEqualTo(code);
    }

    private String publishedLogoDeletion() {
        return publishedEvent(OrganizerLogoDeletedEvent.class).getLogoUrl();
    }

    /** El único evento de ese tipo que se publicó. */
    private <T> T publishedEvent(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<T> matching = captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
        assertThat(matching).hasSize(1);
        return matching.getFirst();
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("logo", "logo.jpg", "image/jpeg", JPEG_BYTES);
    }

    private static OrganizerRequestCreateDto createDto(OrganizerRequestType type) {
        OrganizerRequestCreateDto dto = new OrganizerRequestCreateDto();
        dto.setType(type);
        dto.setInstagram("salsa.loca");
        dto.setContactEmail("info@salsaloca.it");
        dto.setTermsAccepted(true);
        return dto;
    }

    private static OrganizerRequests mappedRequest(OrganizerRequestType type) {
        OrganizerRequests request = new OrganizerRequests();
        request.setType(type);
        request.setInstagram("salsa.loca");
        return request;
    }

    /** status = PENDING por defecto en la entidad. NEW con nombre (el del organizador que se crearía). */
    private static OrganizerRequests request(OrganizerRequestType type, Users owner) {
        OrganizerRequests request = new OrganizerRequests();
        request.setId(REQUEST_ID);
        request.setType(type);
        request.setUser(owner);
        if (type == OrganizerRequestType.NEW) {
            request.setName("Salsa Loca Milano");
        }
        return request;
    }

    private static Organizers organizer(Users owner, OrganizerType type) {
        Organizers organizer = new Organizers();
        organizer.setId(ORGANIZER_ID);
        organizer.setName("Salsa Loca Milano");
        organizer.setType(type);
        organizer.setUser(owner);
        organizer.setClaimed(owner != null);
        organizer.setVerified(true);
        return organizer;
    }

    private static Users user(UUID id) {
        Users user = new Users();
        user.setId(id);
        return user;
    }

    private static OrganizerRequestRejectDto rejectDto(OrganizerRejectReason reason, String message) {
        OrganizerRequestRejectDto dto = new OrganizerRequestRejectDto();
        dto.setReason(reason);
        dto.setMessage(message);
        return dto;
    }

    private static OrganizerRequestApproveDto approveDto(UUID venueId) {
        OrganizerRequestApproveDto dto = new OrganizerRequestApproveDto();
        dto.setVenueId(venueId);
        return dto;
    }
}
