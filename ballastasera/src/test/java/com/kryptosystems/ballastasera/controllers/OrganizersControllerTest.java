package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.OrganizerEventSummaryDto;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.EventSeriesMapper;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizersController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrganizersControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizersService organizersService;

    @MockitoBean
    private OrganizerMapper organizerMapper;

    @MockitoBean
    private EventsService eventsService;

    @MockitoBean
    private EventsMapper eventsMapper;

    @MockitoBean
    private VenuesService venuesService;

    @MockitoBean
    private VenuesMapper venuesMapper;

    @MockitoBean
    private EventSeriesService eventSeriesService;

    @MockitoBean
    private EventSeriesMapper eventSeriesMapper;

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
    void getOrganizerEventsManageReturnsFilteredPage() throws Exception {
        OrganizerEventSummaryDto summary = new OrganizerEventSummaryDto();
        summary.setTitle("Pending event");
        summary.setStatus(EventStatus.PENDING);
        PageRequest pageable = PageRequest.of(0, 5);
        when(eventsService.findManageableByOrganizerId(
                USER_ID, ORGANIZER_ID, EventStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(summary), pageable, 1));

        mockMvc.perform(get("/rest/organizers/{id}/events/manage", ORGANIZER_ID)
                        .param("page", "0")
                        .param("size", "5")
                        .param("status", "PENDING")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(eventsService).findManageableByOrganizerId(
                USER_ID, ORGANIZER_ID, EventStatus.PENDING, pageable);
    }

    @Test
    void getOrganizerEventsManageRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/rest/organizers/{id}/events/manage", ORGANIZER_ID)
                        .param("page", "-1")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/rest/organizers/{id}/events/manage", ORGANIZER_ID)
                        .param("size", "51")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(eventsService);
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        Users user = new Users();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
