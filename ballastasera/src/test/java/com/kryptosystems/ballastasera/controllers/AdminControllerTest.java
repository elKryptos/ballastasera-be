package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.exceptions.core.BackendErrorResponse;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.services.manager.EventSeriesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(BackendErrorResponse.class)
class AdminControllerTest {

    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

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
    private EventSeriesService eventSeriesService;

    @MockitoBean
    private VenuesMapper venuesMapper;

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
        when(eventsService.toEventDetailDto(event)).thenReturn(response);

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
        when(eventsService.toEventDetailDto(event)).thenReturn(response);

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
}
