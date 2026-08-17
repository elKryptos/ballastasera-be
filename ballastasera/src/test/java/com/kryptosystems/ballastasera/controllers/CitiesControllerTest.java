package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.mappers.CitiesMapperImpl;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CitiesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CitiesMapperImpl.class)
class CitiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CitiesService citiesService;

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

        mockMvc.perform(get("/api/cities"))
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

        mockMvc.perform(get("/api/cities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Milano"))
                .andExpect(jsonPath("$.isActive").value(true));
    }
}
