package com.kryptosystems.ballastasera.controllers;

import com.jayway.jsonpath.JsonPath;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 7: traguardi del perfil (serate, locales, primera serata, «Su ballastasera da…»), contra Postgres real. */
class ProfileStatsIntegrationTest extends IntegrationTest {

    private static final OffsetDateTime FIRST_SERATA = OffsetDateTime.parse("2026-03-14T21:00:00+01:00");

    /**
     * Cuentan las serate terminadas: sin end_at, 4 horas despues de empezar; con end_at, cuando termina. Las que
     * siguen en curso y las futuras no. Los locales son los distintos de esas serate, sin las que no tienen local.
     */
    @Test
    void theTraguardiCountOnlyEndedSerate() throws Exception {
        Users ana = createUser("Ana");
        UUID tropical = createVenue("Tropical");
        UUID latino = createVenue("Latino");
        UUID salsaClub = createVenue("Salsa Club");

        attend(ana, serataAt("Prima serata", FIRST_SERATA, tropical));
        attend(ana, serata("Di nuovo al Tropical", "-3 days", tropical));
        attend(ana, serata("Al Latino", "-5 days", latino));
        attend(ana, serata("In piazza", "-2 days", null));
        attend(ana, serataEndingIn("Aperitivo finito", "-3 hours", "-1 hours", salsaClub));
        attend(ana, serataEndingIn("Maratona ancora in corso", "-5 hours", "1 hours", salsaClub));
        attend(ana, serata("Appena iniziata", "-1 hours", salsaClub));
        attend(ana, serata("Domani", "1 day", salsaClub));

        String me = mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.eventsCount").value(5))
                .andExpect(jsonPath("$.venuesCount").value(3))
                .andExpect(jsonPath("$.memberSince").exists())
                .andReturn().getResponse().getContentAsString();
        String firstEventAt = JsonPath.read(me, "$.firstEventAt");
        assertThat(OffsetDateTime.parse(firstEventAt).toInstant()).isEqualTo(FIRST_SERATA.toInstant());
    }

    /** Los demas ven las serate, los locales y desde cuando esta, pero no la fecha de la primera serata. */
    @Test
    void othersSeeTheTraguardiButNotTheFirstSerata() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        UUID tropical = createVenue("Tropical");
        attend(ana, serataAt("Prima serata", FIRST_SERATA, tropical));
        attend(ana, serata("Di nuovo al Tropical", "-3 days", tropical));

        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca)))
                .andExpect(jsonPath("$.eventsCount").value(2))
                .andExpect(jsonPath("$.venuesCount").value(1))
                .andExpect(jsonPath("$.memberSince").exists())
                .andExpect(jsonPath("$.firstEventAt").doesNotExist());
    }

    /** Son contadores, como eventsCount: se ven aunque nadie pueda ver sus serate. */
    @Test
    void theTraguardiAreVisibleEvenWhenTheSerateAreHidden() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        attend(ana, serata("Al Tropical", "-3 days", createVenue("Tropical")));
        mockMvc.perform(patch("/rest/auth/me").with(as(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"showProfilePublic\": true, \"attendanceVisibility\": \"NOBODY\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca)))
                .andExpect(jsonPath("$.eventsCount").value(1))
                .andExpect(jsonPath("$.venuesCount").value(1));
    }

    @Test
    void withoutPastSerateTheTraguardiAreEmpty() throws Exception {
        Users ana = createUser("Ana");
        attend(ana, serata("Domani", "1 day", createVenue("Tropical")));

        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.eventsCount").value(0))
                .andExpect(jsonPath("$.venuesCount").value(0))
                .andExpect(jsonPath("$.firstEventAt").value(nullValue()));
    }

    /** Local en la primera ciudad del seed, como las serate de createEvent. */
    private UUID createVenue(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO venues (name, city_id, address, latitude, longitude)
                VALUES (?, (SELECT id FROM cities ORDER BY id LIMIT 1), 'Via Roma 1', 45.46, 9.19)
                RETURNING id
                """, UUID.class, name);
    }

    /** Serata sin end_at en venueId (null: sin local). startsIn como en createEventStartingIn. */
    private UUID serata(String title, String startsIn, UUID venueId) {
        UUID eventId = createEventStartingIn(title, startsIn);
        jdbcTemplate.update("UPDATE events SET venue_id = ? WHERE id = ?", venueId, eventId);
        return eventId;
    }

    private UUID serataAt(String title, OffsetDateTime startAt, UUID venueId) {
        UUID eventId = serata(title, "-1 day", venueId);
        jdbcTemplate.update("UPDATE events SET start_at = ? WHERE id = ?", startAt, eventId);
        return eventId;
    }

    private UUID serataEndingIn(String title, String startsIn, String endsIn, UUID venueId) {
        UUID eventId = serata(title, startsIn, venueId);
        jdbcTemplate.update("UPDATE events SET end_at = now() + CAST(? AS interval) WHERE id = ?", endsIn, eventId);
        return eventId;
    }
}
