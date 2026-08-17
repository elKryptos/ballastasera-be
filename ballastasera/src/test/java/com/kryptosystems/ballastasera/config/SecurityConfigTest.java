package com.kryptosystems.ballastasera.config;

import com.kryptosystems.ballastasera.models.entities.Users;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void anonymousCannotGetOwnOrganizers() throws Exception {
        mockMvc.perform(get("/api/organizers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anonymousCannotCheckFavorite() throws Exception {
        mockMvc.perform(get("/api/events/{id}/favorite", EVENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void authenticatedUserCanGetOwnOrganizers() throws Exception {
        mockMvc.perform(get("/api/organizers/me")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanCheckFavorite() throws Exception {
        mockMvc.perform(get("/api/events/{id}/favorite", EVENT_ID)
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    void eventDetailRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/events/{id}", EVENT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void citiesRemainPublic() throws Exception {
        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cities/{id}", 1L))
                .andExpect(status().isOk());
    }

    @Test
    void danceStylesRemainPublic() throws Exception {
        mockMvc.perform(get("/api/dance-styles"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dance-styles/{id}", 1L))
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

        @GetMapping("/api/organizers/me")
        String getOwnOrganizers() {
            return "ok";
        }

        @GetMapping("/api/events/{id}/favorite")
        boolean checkFavorite(@PathVariable UUID id) {
            return false;
        }

        @GetMapping("/api/events/{id}")
        String getEvent(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/api/cities")
        String getCities() {
            return "ok";
        }

        @GetMapping("/api/cities/{id}")
        String getCity(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/api/dance-styles")
        String getDanceStyles() {
            return "ok";
        }

        @GetMapping("/api/dance-styles/{id}")
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
