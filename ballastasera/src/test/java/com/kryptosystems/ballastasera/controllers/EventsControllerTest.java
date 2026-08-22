package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.exceptions.core.BackendErrorResponse;
import com.kryptosystems.ballastasera.models.dtos.EventCreateDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.EventUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.FavoritesService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(BackendErrorResponse.class)
class EventsControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventsService eventsService;

    @MockitoBean
    private EventAttendanceService eventAttendanceService;

    @MockitoBean
    private FavoritesService favoritesService;

    @MockitoBean
    private EventsRepository eventsRepository;

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
    void createReturnsCreatedEvent() throws Exception {
        Events event = event();
        EventDetailDto response = detail("Salsa Night");

        when(eventsService.create(eq(USER_ID), any(EventCreateDto.class))).thenReturn(event);
        when(eventsService.toEventDetailDto(event)).thenReturn(response);

        mockMvc.perform(post("/rest/events")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Salsa Night"));

        verify(eventsService).create(eq(USER_ID), any(EventCreateDto.class));
    }

    @Test
    void updateReturnsUpdatedEvent() throws Exception {
        Events event = event();
        EventDetailDto response = detail("Updated Salsa Night");

        when(eventsService.update(eq(EVENT_ID), eq(USER_ID), any(EventUpdateDto.class))).thenReturn(event);
        when(eventsService.toEventDetailDto(event)).thenReturn(response);

        mockMvc.perform(patch("/rest/events/{id}", EVENT_ID)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated Salsa Night"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Salsa Night"));

        verify(eventsService).update(eq(EVENT_ID), eq(USER_ID), any(EventUpdateDto.class));
    }

    @Test
    void updateStatusReturnsUpdatedEvent() throws Exception {
        Events event = event();
        EventDetailDto response = detail("Salsa Night");

        when(eventsService.updateStatus(USER_ID, EVENT_ID,
                com.kryptosystems.ballastasera.enums.EventStatus.PUBLISHED)).thenReturn(event);
        when(eventsService.toEventDetailDto(event)).thenReturn(response);

        mockMvc.perform(patch("/rest/events/{id}/status", EVENT_ID)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Salsa Night"));

        verify(eventsService).updateStatus(
                USER_ID,
                EVENT_ID,
                com.kryptosystems.ballastasera.enums.EventStatus.PUBLISHED
        );
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/rest/events/{id}", EVENT_ID)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isNoContent());

        verify(eventsService).delete(EVENT_ID, USER_ID);
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/rest/events")
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(eventsService, never()).create(any(UUID.class), any(EventCreateDto.class));
    }

    @Test
    void updateStatusRejectsMissingStatus() throws Exception {
        mockMvc.perform(patch("/rest/events/{id}/status", EVENT_ID)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(eventsService, never()).updateStatus(
                any(UUID.class),
                any(UUID.class),
                any()
        );
    }

    @Test
    void updateReturnsForbiddenWhenServiceDeniesAccess() throws Exception {
        when(eventsService.update(eq(EVENT_ID), eq(USER_ID), any(EventUpdateDto.class)))
                .thenThrow(new AccessDeniedException("Not the owner of this event"));

        mockMvc.perform(patch("/rest/events/{id}", EVENT_ID)
                        .with(authentication(userAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Salsa Night\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not the owner of this event"));
    }

    @Test
    void deleteReturnsNotFoundWhenEventDoesNotExist() throws Exception {
        doThrow(new EntityNotFoundException("Event not found with id " + EVENT_ID))
                .when(eventsService)
                .delete(EVENT_ID, USER_ID);

        mockMvc.perform(delete("/rest/events/{id}", EVENT_ID)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found with id " + EVENT_ID));
    }

    private String validCreateJson() {
        return """
                {
                  "organizerId": "10000000-0000-0000-0000-000000000001",
                  "cityId": 1,
                  "title": "Salsa Night",
                  "startAt": "2099-01-01T19:00:00+01:00",
                  "endAt": "2099-01-01T22:00:00+01:00",
                  "address": "Via Roma 1"
                }
                """;
    }

    private Events event() {
        Events event = new Events();
        event.setId(EVENT_ID);
        return event;
    }

    private EventDetailDto detail(String title) {
        EventDetailDto detail = new EventDetailDto();
        detail.setTitle(title);
        return detail;
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
