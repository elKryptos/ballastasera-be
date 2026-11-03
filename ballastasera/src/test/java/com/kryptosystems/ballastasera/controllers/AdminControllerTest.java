package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;
import com.kryptosystems.ballastasera.exceptions.OrganizerRequestConflictException;
import com.kryptosystems.ballastasera.exceptions.core.BackendErrorResponse;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestAdminDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestRejectDto;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.models.mappers.OrganizerRequestMapper;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.OrganizerRequestsService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(BackendErrorResponse.class)
class AdminControllerTest {

    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String LOGO_URL = "http://localhost/logos/venues/" + VENUE_ID + "/logo-abc.webp";
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final UUID REQUEST_ID = UUID.fromString("ab000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizersService organizersService;

    @MockitoBean
    private OrganizerMapper organizerMapper;

    @MockitoBean
    private VenuesService venuesService;

    @MockitoBean
    private EventsService eventsService;

    @MockitoBean
    private EventsMapper eventsMapper;

    @MockitoBean
    private EventSeriesService eventSeriesService;

    @MockitoBean
    private VenuesMapper venuesMapper;

    @MockitoBean
    private OrganizerRequestsService organizerRequestsService;

    @MockitoBean
    private OrganizerRequestMapper organizerRequestMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void updateEventFlyerReturnsUpdatedEvent() throws Exception {
        Events event = new Events();
        event.setId(EVENT_ID);
        EventDetailDto response = new EventDetailDto();
        response.setTitle("Salsa Night");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "flyer.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );

        when(eventsService.updateFlyerAsAdmin(eq(EVENT_ID), any())).thenReturn(event);
        when(eventsMapper.toEventDetailDto(event)).thenReturn(response);

        mockMvc.perform(multipart("/rest/admin/events/{id}/flyer", EVENT_ID)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Salsa Night"));

        verify(eventsService).updateFlyerAsAdmin(eq(EVENT_ID), any(MultipartFile.class));
    }

    @Test
    void updateEventFlyerReturnsNotFoundWhenEventDoesNotExist() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "flyer.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );
        when(eventsService.updateFlyerAsAdmin(eq(EVENT_ID), any(MultipartFile.class)))
                .thenThrow(new EntityNotFoundException("Event not found with id " + EVENT_ID));

        mockMvc.perform(multipart("/rest/admin/events/{id}/flyer", EVENT_ID)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found with id " + EVENT_ID));
    }

    @Test
    void deleteEventFlyerReturnsUpdatedEvent() throws Exception {
        Events event = new Events();
        event.setId(EVENT_ID);
        EventDetailDto response = new EventDetailDto();
        response.setTitle("Salsa Night");

        when(eventsService.deleteFlyerAsAdmin(EVENT_ID)).thenReturn(event);
        when(eventsMapper.toEventDetailDto(event)).thenReturn(response);

        mockMvc.perform(delete("/rest/admin/events/{id}/flyer", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Salsa Night"));

        verify(eventsService).deleteFlyerAsAdmin(EVENT_ID);
    }

    @Test
    void deleteEventFlyerReturnsNotFoundWhenEventDoesNotExist() throws Exception {
        when(eventsService.deleteFlyerAsAdmin(EVENT_ID))
                .thenThrow(new EntityNotFoundException("Event not found with id " + EVENT_ID));

        mockMvc.perform(delete("/rest/admin/events/{id}/flyer", EVENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found with id " + EVENT_ID));
    }

    @Test
    void updateVenueLogoReturnsUpdatedVenue() throws Exception {
        Venues venue = new Venues();
        venue.setId(VENUE_ID);
        VenueDetailDto response = new VenueDetailDto();
        response.setName("Sala Havana");
        response.setLogoUrl(LOGO_URL);

        when(venuesService.updateLogoAsAdmin(eq(VENUE_ID), any(MultipartFile.class))).thenReturn(venue);
        when(venuesMapper.toVenueDetailDto(venue)).thenReturn(response);

        mockMvc.perform(multipart("/rest/admin/venues/{id}/logo", VENUE_ID)
                        .file(logoFile())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sala Havana"))
                .andExpect(jsonPath("$.logoUrl").value(LOGO_URL));

        verify(venuesService).updateLogoAsAdmin(eq(VENUE_ID), any(MultipartFile.class));
    }

    @Test
    void updateVenueLogoReturnsUnsupportedMediaTypeWhenFileIsNotAnImage() throws Exception {
        when(venuesService.updateLogoAsAdmin(eq(VENUE_ID), any(MultipartFile.class)))
                .thenThrow(new InvalidMediaTypeException("File is not a valid JPEG, PNG or WebP image"));

        mockMvc.perform(multipart("/rest/admin/venues/{id}/logo", VENUE_ID)
                        .file(logoFile())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("File is not a valid JPEG, PNG or WebP image"));
    }

    @Test
    void updateVenueLogoReturnsNotFoundWhenVenueDoesNotExist() throws Exception {
        when(venuesService.updateLogoAsAdmin(eq(VENUE_ID), any(MultipartFile.class)))
                .thenThrow(new EntityNotFoundException("Venue not found with id " + VENUE_ID));

        mockMvc.perform(multipart("/rest/admin/venues/{id}/logo", VENUE_ID)
                        .file(logoFile())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Venue not found with id " + VENUE_ID));
    }

    @Test
    void updateVenueLogoReturnsBadRequestWhenLogoPartIsMissing() throws Exception {
        MockMultipartFile wrongPart = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/rest/admin/venues/{id}/logo", VENUE_ID)
                        .file(wrongPart)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing request part: logo"));

        verifyNoInteractions(venuesService);
    }

    @Test
    void deleteVenueLogoReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/rest/admin/venues/{id}/logo", VENUE_ID))
                .andExpect(status().isNoContent());

        verify(venuesService).deleteLogoAsAdmin(VENUE_ID);
    }

    @Test
    void deleteVenueLogoReturnsNotFoundWhenVenueDoesNotExist() throws Exception {
        when(venuesService.deleteLogoAsAdmin(VENUE_ID))
                .thenThrow(new EntityNotFoundException("Venue not found with id " + VENUE_ID));

        mockMvc.perform(delete("/rest/admin/venues/{id}/logo", VENUE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Venue not found with id " + VENUE_ID));
    }

    // ---------- solicitudes «Diventa organizzatore» ----------

    @Test
    void getOrganizerRequestsReturnsThePageOfTheStatus() throws Exception {
        OrganizerRequests request = new OrganizerRequests();
        request.setType(OrganizerRequestType.NEW);
        OrganizerRequestAdminDto dto = new OrganizerRequestAdminDto();
        dto.setStatus(OrganizerRequestStatus.PENDING);
        when(organizerRequestsService.findByStatus(eq(OrganizerRequestStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(organizerRequestMapper.toOrganizerRequestAdminDto(eq(request), anyLong(), eq(List.of()))).thenReturn(dto);

        mockMvc.perform(get("/rest/admin/organizer-requests").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void getOrganizerRequestsWithoutStatusIsABadRequest() throws Exception {
        mockMvc.perform(get("/rest/admin/organizer-requests"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(organizerRequestsService);
    }

    @Test
    void approveOrganizerRequestReturnsTheApprovedRequest() throws Exception {
        authenticateAsAdmin();
        OrganizerRequests request = new OrganizerRequests();
        request.setType(OrganizerRequestType.NEW);
        OrganizerRequestAdminDto dto = new OrganizerRequestAdminDto();
        dto.setStatus(OrganizerRequestStatus.APPROVED);
        when(organizerRequestsService.approve(REQUEST_ID, ADMIN_ID, null)).thenReturn(request);
        when(organizerRequestMapper.toOrganizerRequestAdminDto(eq(request), anyLong(), eq(List.of()))).thenReturn(dto);

        mockMvc.perform(patch("/rest/admin/organizer-requests/{id}/approve", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveOrganizerRequestReturnsConflictWithCode() throws Exception {
        authenticateAsAdmin();
        when(organizerRequestsService.approve(eq(REQUEST_ID), eq(ADMIN_ID), any()))
                .thenThrow(new OrganizerRequestConflictException(OrganizerRequestConflictException.VENUE_REQUIRED,
                        "Organizer type CLUB requires a venue"));

        mockMvc.perform(patch("/rest/admin/organizer-requests/{id}/approve", REQUEST_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("VENUE_REQUIRED"));
    }

    @Test
    void rejectOrganizerRequestReturnsTheRejectedRequest() throws Exception {
        authenticateAsAdmin();
        OrganizerRequests request = new OrganizerRequests();
        request.setType(OrganizerRequestType.NEW);
        OrganizerRequestAdminDto dto = new OrganizerRequestAdminDto();
        dto.setStatus(OrganizerRequestStatus.REJECTED);
        when(organizerRequestsService.reject(eq(REQUEST_ID), eq(ADMIN_ID), any(OrganizerRequestRejectDto.class))).thenReturn(request);
        when(organizerRequestMapper.toOrganizerRequestAdminDto(eq(request), anyLong(), eq(List.of()))).thenReturn(dto);

        mockMvc.perform(patch("/rest/admin/organizer-requests/{id}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"INSTAGRAM_NOT_FOUND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    /** OTHER exige mensaje: lo valida el DTO (@AssertTrue isMessageProvidedForOther). */
    @Test
    void rejectWithOtherAndNoMessageIsABadRequest() throws Exception {
        authenticateAsAdmin();

        mockMvc.perform(patch("/rest/admin/organizer-requests/{id}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.messageProvidedForOther").exists());

        verifyNoInteractions(organizerRequestsService);
    }

    @Test
    void rejectOfARequestThatIsNoLongerPendingIsAConflict() throws Exception {
        authenticateAsAdmin();
        when(organizerRequestsService.reject(eq(REQUEST_ID), eq(ADMIN_ID), any(OrganizerRequestRejectDto.class)))
                .thenThrow(new OrganizerRequestConflictException(OrganizerRequestConflictException.REQUEST_NOT_PENDING,
                        "Only pending requests can be reviewed"));

        mockMvc.perform(patch("/rest/admin/organizer-requests/{id}/reject", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"INSTAGRAM_NOT_FOUND\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("REQUEST_NOT_PENDING"));
    }

    @Test
    void unclaimOrganizerReturnsConflictWhenItHasNoOwner() throws Exception {
        when(organizersService.unclaimAsAdmin(ORGANIZER_ID))
                .thenThrow(new OrganizerRequestConflictException(OrganizerRequestConflictException.ORGANIZER_NOT_CLAIMED,
                        "Organizer " + ORGANIZER_ID + " has no owner"));

        mockMvc.perform(patch("/rest/admin/organizers/{id}/unclaim", ORGANIZER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("ORGANIZER_NOT_CLAIMED"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** approve y reject leen el id del admin de @AuthenticationPrincipal. */
    private void authenticateAsAdmin() {
        Users admin = new Users();
        admin.setId(ADMIN_ID);
        admin.setEmail("admin@example.com");
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private MockMultipartFile logoFile() {
        return new MockMultipartFile("logo", "logo.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }
}
