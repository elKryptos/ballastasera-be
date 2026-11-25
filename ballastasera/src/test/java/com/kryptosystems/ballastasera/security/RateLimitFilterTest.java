package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.security.RateLimitService.RateLimitResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final RateLimitFilter filter = new RateLimitFilter(rateLimitService, JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        when(rateLimitService.isEnabled()).thenReturn(true);
        when(rateLimitService.tryConsume(any(), anyString())).thenReturn(new RateLimitResult(true, 0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousGetIsReadByIp() throws Exception {
        MockHttpServletRequest request = request("GET", "/rest/events");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(rateLimitService).tryConsume(RateLimitGroup.READ, "ip:10.0.0.1");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void authenticatedPostIsWriteByUser() throws Exception {
        authenticate();

        filter.doFilter(request("POST", "/rest/events/123/attendance"), new MockHttpServletResponse(), chain);

        verify(rateLimitService).tryConsume(RateLimitGroup.WRITE, "user:" + USER_ID);
    }

    @Test
    void socialActionsAreSocial() throws Exception {
        authenticate();

        filter.doFilter(request("POST", "/rest/users/" + UUID.randomUUID() + "/follow"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("DELETE", "/rest/users/" + UUID.randomUUID() + "/block"), new MockHttpServletResponse(), chain);

        verify(rateLimitService, times(2)).tryConsume(RateLimitGroup.SOCIAL, "user:" + USER_ID);
    }

    @Test
    void readingASocialPathIsRead() {
        assertThat(RateLimitFilter.resolveGroup(request("GET", "/rest/users/me/blocks"))).isEqualTo(RateLimitGroup.READ);
    }

    @Test
    void multipartIsUpload() {
        MockHttpServletRequest request = request("PATCH", "/rest/organizers/123/logo");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=abc");

        assertThat(RateLimitFilter.resolveGroup(request)).isEqualTo(RateLimitGroup.UPLOAD);
    }

    @Test
    void loginIsAuthByIpEvenWithAUser() throws Exception {
        authenticate();

        filter.doFilter(request("GET", "/oauth2/authorization/google"), new MockHttpServletResponse(), chain);

        verify(rateLimitService).tryConsume(RateLimitGroup.AUTH, "ip:10.0.0.1");
    }

    @Test
    void overTheLimitReturns429WithRetryAfter() throws Exception {
        when(rateLimitService.tryConsume(any(), anyString())).thenReturn(new RateLimitResult(false, 12));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/rest/events"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("12");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("\"path\":\"/rest/events\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void disabledSkipsTheLimit() throws Exception {
        when(rateLimitService.isEnabled()).thenReturn(false);

        filter.doFilter(request("GET", "/rest/events"), new MockHttpServletResponse(), chain);

        verify(rateLimitService, never()).tryConsume(any(), anyString());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void corsPreflightAndHealthAreNotLimited() throws Exception {
        filter.doFilter(request("OPTIONS", "/rest/events"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("GET", "/rest/health"), new MockHttpServletResponse(), chain);

        verify(rateLimitService, never()).tryConsume(any(), anyString());
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    private static void authenticate() {
        Users user = new Users();
        user.setId(USER_ID);
        user.setEmail("ana@example.com");
        user.setRole(UserRole.USER);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
