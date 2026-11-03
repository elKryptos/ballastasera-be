package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.exceptions.OrganizerRequestConflictException;
import com.kryptosystems.ballastasera.exceptions.core.BackendErrorResponse;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestDto;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.OrganizerRequestMapper;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.OrganizerRequestsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizerRequestsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(BackendErrorResponse.class)
class OrganizerRequestsControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("ab000000-0000-0000-0000-000000000001");
    private static final String LOGO_URL = "http://localhost/logos/organizers/" + REQUEST_ID + "/logo-1.webp";

    /** NEW de tipo PERSON válida: sin dirección del local. */
    private static final String VALID_NEW = """
            {"type": "NEW", "name": "Prova Persona", "organizerType": "PERSON", "instagram": "prova.persona",
             "contactEmail": "prova@example.com", "termsAccepted": true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizerRequestsService organizerRequestsService;

    @MockitoBean
    private OrganizerRequestMapper organizerRequestMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void authenticateControllerRequests() {
        SecurityContextHolder.getContext().setAuthentication(userAuthentication());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturnsCreatedRequest() throws Exception {
        OrganizerRequests request = new OrganizerRequests();
        when(organizerRequestsService.create(eq(USER_ID), any(OrganizerRequestCreateDto.class))).thenReturn(request);
        when(organizerRequestMapper.toOrganizerRequestDto(request)).thenReturn(dto(OrganizerRequestStatus.PENDING, null));

        mockMvc.perform(post("/rest/organizer-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_NEW))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void createRejectsInvalidBody(String description, String body, String field) throws Exception {
        mockMvc.perform(post("/rest/organizer-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());

        verifyNoInteractions(organizerRequestsService);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("NEW sin nombre", """
                        {"type": "NEW", "organizerType": "PERSON", "instagram": "prova", "contactEmail": "a@b.it",
                         "termsAccepted": true}""", "name"),
                Arguments.of("CLAIM sin organizador", """
                        {"type": "CLAIM", "instagram": "prova", "contactEmail": "a@b.it", "termsAccepted": true}""",
                        "targetOrganizerId"),
                Arguments.of("NEW CLUB sin dirección", """
                        {"type": "NEW", "name": "Club", "organizerType": "CLUB", "instagram": "prova",
                         "contactEmail": "a@b.it", "termsAccepted": true}""", "venueAddress"),
                Arguments.of("NEW PERSON con dirección", """
                        {"type": "NEW", "name": "Persona", "organizerType": "PERSON", "instagram": "prova",
                         "contactEmail": "a@b.it", "venueAddress": "Via Roma 1", "venueCityId": 1,
                         "termsAccepted": true}""", "venueAddress"),
                Arguments.of("dirección sin ciudad", """
                        {"type": "NEW", "name": "Club", "organizerType": "CLUB", "instagram": "prova",
                         "contactEmail": "a@b.it", "venueAddress": "Via Roma 1", "termsAccepted": true}""",
                        "venueAddress"),
                Arguments.of("términos sin aceptar", """
                        {"type": "NEW", "name": "Persona", "organizerType": "PERSON", "instagram": "prova",
                         "contactEmail": "a@b.it", "termsAccepted": false}""", "termsAccepted"),
                Arguments.of("Instagram inválido", """
                        {"type": "NEW", "name": "Persona", "organizerType": "PERSON", "instagram": "no valido!",
                         "contactEmail": "a@b.it", "termsAccepted": true}""", "instagram")
        );
    }

    @Test
    void createReturnsConflictWithCode() throws Exception {
        when(organizerRequestsService.create(eq(USER_ID), any(OrganizerRequestCreateDto.class)))
                .thenThrow(new OrganizerRequestConflictException(OrganizerRequestConflictException.PENDING_REQUEST_EXISTS,
                        "User already has a pending request"));

        mockMvc.perform(post("/rest/organizer-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_NEW))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("PENDING_REQUEST_EXISTS"));
    }

    @Test
    void getMyLatestReturnsNoContentWhenTheUserNeverAsked() throws Exception {
        when(organizerRequestsService.findMyLatest(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/rest/organizer-requests/me"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getMyLatestReturnsTheLatestRequest() throws Exception {
        OrganizerRequests request = new OrganizerRequests();
        when(organizerRequestsService.findMyLatest(USER_ID)).thenReturn(Optional.of(request));
        when(organizerRequestMapper.toOrganizerRequestDto(request)).thenReturn(dto(OrganizerRequestStatus.REJECTED, null));

        mockMvc.perform(get("/rest/organizer-requests/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void cancelReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/rest/organizer-requests/{id}", REQUEST_ID))
                .andExpect(status().isNoContent());

        verify(organizerRequestsService).cancel(REQUEST_ID, USER_ID);
    }

    @Test
    void acknowledgeReturnsTheRequest() throws Exception {
        OrganizerRequests request = new OrganizerRequests();
        when(organizerRequestsService.acknowledge(REQUEST_ID, USER_ID)).thenReturn(request);
        when(organizerRequestMapper.toOrganizerRequestDto(request)).thenReturn(dto(OrganizerRequestStatus.APPROVED, null));

        mockMvc.perform(patch("/rest/organizer-requests/{id}/acknowledge", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void updateLogoReturnsTheRequestWithItsLogo() throws Exception {
        OrganizerRequests request = new OrganizerRequests();
        when(organizerRequestsService.updateLogo(eq(REQUEST_ID), eq(USER_ID), any(MultipartFile.class))).thenReturn(request);
        when(organizerRequestMapper.toOrganizerRequestDto(request)).thenReturn(dto(OrganizerRequestStatus.PENDING, LOGO_URL));

        mockMvc.perform(logoUpload())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(LOGO_URL));
    }

    @Test
    void updateLogoReturnsConflictWhenTheLogoIsNotAllowed() throws Exception {
        when(organizerRequestsService.updateLogo(eq(REQUEST_ID), eq(USER_ID), any(MultipartFile.class)))
                .thenThrow(new OrganizerRequestConflictException(OrganizerRequestConflictException.LOGO_NOT_ALLOWED,
                        "The logo is uploaded once, only for a new organizer"));

        mockMvc.perform(logoUpload())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("LOGO_NOT_ALLOWED"));
    }

    @Test
    void updateLogoWithoutTheLogoPartIsABadRequest() throws Exception {
        MockMultipartFile wrongPart = new MockMultipartFile("file", "logo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/rest/organizer-requests/{id}/logo", REQUEST_ID)
                        .file(wrongPart)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(organizerRequestsService);
    }

    private org.springframework.test.web.servlet.RequestBuilder logoUpload() {
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
        return multipart("/rest/organizer-requests/{id}/logo", REQUEST_ID)
                .file(logo)
                .with(request -> {
                    request.setMethod("PATCH");
                    return request;
                });
    }

    private static OrganizerRequestDto dto(OrganizerRequestStatus status, String logoUrl) {
        OrganizerRequestDto dto = new OrganizerRequestDto();
        dto.setStatus(status);
        dto.setLogoUrl(logoUrl);
        return dto;
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        Users user = new Users();
        user.setId(USER_ID);
        user.setEmail("user@example.com");

        UserPrincipal principal = new UserPrincipal(user);

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }
}
