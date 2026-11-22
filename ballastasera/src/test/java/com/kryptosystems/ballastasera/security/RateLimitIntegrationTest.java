package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con Redis real: los buckets se guardan y se leen de verdad. Limite de lectura bajo para no hacer 300 requests. */
@TestPropertySource(properties = "app.rate-limit.read-per-minute=3")
class RateLimitIntegrationTest extends IntegrationTest {

    @Test
    void fourthReadInAMinuteGets429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/rest/cities")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/rest/cities"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors.code").value(RateLimitFilter.RATE_LIMITED))
                .andExpect(jsonPath("$.path").value("/rest/cities"));
    }

    @Test
    void eachIpHasItsOwnBucket() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/rest/cities").with(remoteAddr("10.0.0.1"))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/rest/cities").with(remoteAddr("10.0.0.1"))).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/rest/cities").with(remoteAddr("10.0.0.2"))).andExpect(status().isOk());
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
