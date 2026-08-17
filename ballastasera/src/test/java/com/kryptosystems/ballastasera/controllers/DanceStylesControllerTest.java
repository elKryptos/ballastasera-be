package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.mappers.DanceStylesMapperImpl;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.services.manager.DanceStylesService;
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

@WebMvcTest(DanceStylesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DanceStylesMapperImpl.class)
class DanceStylesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DanceStylesService danceStylesService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getDanceStylesReturnsDanceStyles() throws Exception {
        DanceStyles danceStyle = new DanceStyles();
        danceStyle.setId(1L);
        danceStyle.setName("Bachata");
        danceStyle.setSlug("bachata");

        when(danceStylesService.findAll())
                .thenReturn(List.of(danceStyle));

        mockMvc.perform(get("/api/dance-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Bachata"))
                .andExpect(jsonPath("$[0].slug").value("bachata"));
    }

    @Test
    void getDanceStyleReturnsDanceStyleById() throws Exception {
        DanceStyles danceStyle = new DanceStyles();
        danceStyle.setId(1L);
        danceStyle.setName("Bachata");
        danceStyle.setSlug("bachata");

        when(danceStylesService.findById(1L))
                .thenReturn(danceStyle);

        mockMvc.perform(get("/api/dance-styles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Bachata"))
                .andExpect(jsonPath("$.slug").value("bachata"));
    }
}
