package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.exceptions.core.BackendErrorResponse;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.mappers.CitiesMapperImpl;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CitiesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CitiesMapperImpl.class, BackendErrorResponse.class})
class CitiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CitiesService citiesService;

    @MockitoBean
    private EventsService eventsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getCitiesReturnsActiveCities() throws Exception {
        Cities city = new Cities();
        city.setId(1L);
        city.setName("Milano");
        city.setActive(true);

        when(citiesService.findActive())
                .thenReturn(List.of(city));

        mockMvc.perform(get("/rest/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Milano"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void getCityReturnsActiveCityById() throws Exception {
        Cities city = new Cities();
        city.setId(1L);
        city.setName("Milano");
        city.setActive(true);

        when(citiesService.findActiveById(1L))
                .thenReturn(city);

        mockMvc.perform(get("/rest/cities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Milano"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void getCityEventsUsesDefaultPagination() throws Exception {
        Cities city = city(1L, "milano");
        when(citiesService.findActiveBySlug("milano")).thenReturn(city);
        when(eventsService.findPublicByCity(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/rest/cities/milano/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.empty").value(true));

        verify(citiesService).findActiveBySlug("milano");
        verify(eventsService).findPublicByCity(1L, PageRequest.of(0, 20));
    }

    @Test
    void getCityEventsReturnsPageWithRequestedPagination() throws Exception {
        Cities city = city(7L, "roma");
        EventCardDto card = new EventCardDto();
        card.setTitle("Salsa Night");
        PageRequest pageable = PageRequest.of(1, 2);

        when(citiesService.findActiveBySlug("roma")).thenReturn(city);
        when(eventsService.findPublicByCity(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(card), pageable, 3));

        mockMvc.perform(get("/rest/cities/roma/events")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Salsa Night"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.empty").value(false));
    }

    @Test
    void getCityEventsRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/rest/cities/milano/events").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("400 BAD_REQUEST"));

        mockMvc.perform(get("/rest/cities/milano/events").param("size", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/rest/cities/milano/events").param("size", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/rest/cities/milano/events").param("page", "invalid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(citiesService, eventsService);
    }

    @Test
    void getCityEventsReturnsNotFoundForUnavailableCity() throws Exception {
        when(citiesService.findActiveBySlug("hidden"))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("City not found with slug hidden"));

        mockMvc.perform(get("/rest/cities/hidden/events"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("City not found with slug hidden"));
    }

    private Cities city(Long id, String slug) {
        Cities city = new Cities();
        city.setId(id);
        city.setName(slug);
        city.setSlug(slug);
        city.setActive(true);
        return city;
    }
}
