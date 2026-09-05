package com.kryptosystems.ballastasera.config;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.RevokedTokensRepository;
import com.kryptosystems.ballastasera.security.CustomOidcUserService;
import com.kryptosystems.ballastasera.security.JwtAuthenticationFilter;
import com.kryptosystems.ballastasera.security.JwtService;
import com.kryptosystems.ballastasera.security.OAuth2LoginSuccessHandler;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        useDefaultFilters = false,
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=test-client",
                "spring.security.oauth2.client.registration.google.client-secret=test-secret"
        }
)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityConfigTest.TestConfig.class})
class SecurityConfigTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UsersService usersService;

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private RevokedTokensRepository revokedTokensRepository;

    @Test
    void anonymousCannotGetOwnOrganizers() throws Exception {
        mockMvc.perform(get("/rest/organizers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anonymousCannotCheckFavorite() throws Exception {
        mockMvc.perform(get("/rest/events/{id}/favorite", EVENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anonymousCannotCreateEvents() throws Exception {
        mockMvc.perform(post("/rest/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anonymousCannotUpdateEvents() throws Exception {
        mockMvc.perform(patch("/rest/events/{id}", EVENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anonymousCannotUpdateEventStatus() throws Exception {
        mockMvc.perform(patch("/rest/events/{id}/status", EVENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anonymousCannotDeleteEvents() throws Exception {
        mockMvc.perform(delete("/rest/events/{id}", EVENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void authenticatedUserCanGetOwnOrganizers() throws Exception {
        mockMvc.perform(get("/rest/organizers/me")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanCheckFavorite() throws Exception {
        mockMvc.perform(get("/rest/events/{id}/favorite", EVENT_ID)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    void eventDetailRemainsPublic() throws Exception {
        mockMvc.perform(get("/rest/events/{id}", EVENT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void citiesRemainPublic() throws Exception {
        mockMvc.perform(get("/rest/cities"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/rest/cities/{id}", 1L))
                .andExpect(status().isOk());
    }

    @Test
    void cityEventsRemainPublic() throws Exception {
        mockMvc.perform(get("/rest/cities/milano/events"))
                .andExpect(status().isOk());
    }

    @Test
    void danceStylesRemainPublic() throws Exception {
        mockMvc.perform(get("/rest/dance-styles"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/rest/dance-styles/{id}", 1L))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        Users user = new Users();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        user.setEmail("user@example.com");

        UserPrincipal principal = new UserPrincipal(user);

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    @RestController
    static class TestController {

        @GetMapping("/rest/organizers/me")
        String getOwnOrganizers() {
            return "ok";
        }

        @GetMapping("/rest/events/{id}/favorite")
        boolean checkFavorite(@PathVariable UUID id) {
            return false;
        }

        @PostMapping("/rest/events")
        String createEvent() {
            return "ok";
        }

        @PatchMapping("/rest/events/{id}")
        String updateEvent(@PathVariable UUID id) {
            return "ok";
        }

        @PatchMapping("/rest/events/{id}/status")
        String updateEventStatus(@PathVariable UUID id) {
            return "ok";
        }

        @DeleteMapping("/rest/events/{id}")
        void deleteEvent(@PathVariable UUID id) {
        }

        @GetMapping("/rest/events/{id}")
        String getEvent(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/rest/cities")
        String getCities() {
            return "ok";
        }

        @GetMapping("/rest/cities/{id}")
        String getCity(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/rest/cities/{slug}/events")
        String getCityEvents(@PathVariable String slug) {
            return "ok";
        }

        @GetMapping("/rest/dance-styles")
        String getDanceStyles() {
            return "ok";
        }

        @GetMapping("/rest/dance-styles/{id}")
        String getDanceStyle(@PathVariable Long id) {
            return "ok";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }
}
