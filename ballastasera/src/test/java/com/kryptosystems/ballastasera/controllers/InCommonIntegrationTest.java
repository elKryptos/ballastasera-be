package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 3: serate de otra persona, «Avete in comune», amigos que van y «Cerco qualcuno», contra Postgres real. */
class InCommonIntegrationTest extends IntegrationTest {

    @Test
    void theirUpcomingSerateAreVisibleByDefault() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        attend(luca, createEvent("Salsa al Tropical"));
        attend(luca, createEventStartingIn("Bachata di ieri", "-3 days"));

        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").with(as(ana)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Salsa al Tropical"));
        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").param("past", "true").with(as(ana)))
                .andExpect(jsonPath("$.items[0].title").value("Bachata di ieri"));
    }

    /** «Solo amici»: lo ve un amigo, no quien solo lo sigue. «Nessuno»: nadie, salvo el dueño. */
    @Test
    void theirSerateFollowWhoSeesYourSerate() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users marco = createUser("Marco");
        attend(luca, createEvent("Salsa al Tropical"));
        befriend(ana, luca);
        follow(marco, luca);

        saveProfile(luca, "MUTUALS");
        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").with(as(ana)))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").with(as(marco)))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        saveProfile(luca, "NOBODY");
        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").with(as(ana)))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").with(as(luca)))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void theSerateOfABlockedUserAreNotFound() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        mockMvc.perform(post("/rest/users/" + ana.getId() + "/block").with(as(luca))).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/" + luca.getId() + "/attendance").with(as(ana)))
                .andExpect(status().isNotFound());
    }

    /** Sara y Marco son amigos de los dos; Giulia solo de Ana. Fueron juntos a una serata pasada. */
    @Test
    void theProfileShowsWhatTheyHaveInCommon() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        Users marco = createUser("Marco");
        Users giulia = createUser("Giulia");
        befriend(ana, sara);
        befriend(luca, sara);
        befriend(ana, marco);
        befriend(luca, marco);
        befriend(ana, giulia);
        UUID lastWeek = createEventStartingIn("Kizomba della settimana scorsa", "-7 days");
        UUID yesterday = createEventStartingIn("Bachata di ieri", "-1 days");
        for (UUID eventId : new UUID[]{lastWeek, yesterday}) {
            attend(ana, eventId);
            attend(luca, eventId);
        }

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.mutualFriendsCount").value(2))
                .andExpect(jsonPath("$.mutualFriends[*].displayName").value(contains("Marco", "Sara")))
                .andExpect(jsonPath("$.mutualFriends[0].relation").value("MUTUAL"))
                .andExpect(jsonPath("$.eventsTogetherCount").value(2))
                .andExpect(jsonPath("$.lastEventTogetherId").value(yesterday.toString()))
                .andExpect(jsonPath("$.lastEventTogetherTitle").value("Bachata di ieri"));
    }

    /** Si Luca esconde sus serate, Ana no ve las serate juntos (diria a donde fue); los amigos en comun si. */
    @Test
    void hiddenSerateHideTheSerateTogether() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        befriend(ana, sara);
        befriend(luca, sara);
        UUID yesterday = createEventStartingIn("Bachata di ieri", "-1 days");
        attend(ana, yesterday);
        attend(luca, yesterday);
        saveProfile(luca, "NOBODY");

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.mutualFriendsCount").value(1))
                .andExpect(jsonPath("$.eventsTogetherCount").value(0))
                .andExpect(jsonPath("$.lastEventTogetherId").value(nullValue()));
    }

    /** Sin body no cambia la marca; con body la pone o la quita. */
    @Test
    void lookingForPartnerIsMarkedPerSerata() throws Exception {
        Users luca = createUser("Luca");
        UUID eventId = createEvent("Salsa al Tropical");

        attendWith(luca, eventId, "{ \"lookingForPartner\": true }");
        assertMyAttendance(luca, eventId, true);

        mockMvc.perform(post("/rest/events/" + eventId + "/attendance").with(as(luca))).andExpect(status().isNoContent());
        assertMyAttendance(luca, eventId, true);

        attendWith(luca, eventId, "{ \"lookingForPartner\": false }");
        assertMyAttendance(luca, eventId, false);

        mockMvc.perform(get("/rest/events/" + eventId).with(as(luca)))
                .andExpect(jsonPath("$.goingCount").value(1));
    }

    @Test
    void notGoingMeansNotLookingEither() throws Exception {
        Users luca = createUser("Luca");
        UUID eventId = createEvent("Salsa al Tropical");

        mockMvc.perform(get("/rest/events/" + eventId + "/attendance/me").with(as(luca)))
                .andExpect(jsonPath("$.going").value(false))
                .andExpect(jsonPath("$.lookingForPartner").value(false));
    }

    /** «Chi ci va» trae la marca de cada uno y, con login, la relacion con quien mira. */
    @Test
    void chiCiVaCarriesTheMarkAndTheRelation() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users marco = createUser("Marco");
        UUID eventId = createEvent("Salsa al Tropical");
        befriend(ana, luca);
        attendWith(luca, eventId, "{ \"lookingForPartner\": true }");
        attend(marco, eventId);

        mockMvc.perform(get("/rest/events/" + eventId + "/attendees").with(as(ana)))
                .andExpect(jsonPath("$.content[0].displayName").value("Luca"))
                .andExpect(jsonPath("$.content[0].lookingForPartner").value(true))
                .andExpect(jsonPath("$.content[0].relation").value("MUTUAL"))
                .andExpect(jsonPath("$.content[1].relation").value("NONE"));
        mockMvc.perform(get("/rest/events/" + eventId + "/attendees"))
                .andExpect(jsonPath("$.content[0].relation").value(nullValue()));
    }

    /** El pallino del perfil: solo por una serata en curso o de las proximas 24 horas. */
    @Test
    void theProfileShowsThePallinoOnlyForASerataSoon() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        UUID nextWeek = createEventStartingIn("La prossima settimana", "7 days");
        attendWith(luca, nextWeek, "{ \"lookingForPartner\": true }");

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.lookingForPartnerToday").value(false));

        UUID tonight = createEventStartingIn("Stasera", "3 hours");
        attendWith(luca, tonight, "{ \"lookingForPartner\": true }");
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.lookingForPartnerToday").value(true));
    }

    /** Solo amigos, y no los que eligieron que nadie vea sus serate. */
    @Test
    void friendsGoingListsOnlyFriendsWhoShowTheirSerate() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        Users marco = createUser("Marco");
        UUID eventId = createEvent("Salsa al Tropical");
        befriend(ana, luca);
        befriend(ana, sara);
        follow(ana, marco);
        attend(luca, eventId);
        attend(sara, eventId);
        attend(marco, eventId);
        saveProfile(sara, "NOBODY");

        mockMvc.perform(get("/rest/events/" + eventId + "/friends-going").with(as(ana)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Luca"))
                .andExpect(jsonPath("$[0].relation").value("MUTUAL"));
    }

    private void follow(Users follower, Users followed) throws Exception {
        mockMvc.perform(post("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }

    private void befriend(Users a, Users b) throws Exception {
        follow(a, b);
        follow(b, a);
    }

    private void saveProfile(Users user, String attendanceVisibility) throws Exception {
        mockMvc.perform(patch("/rest/auth/me").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"showProfilePublic\": true, \"attendanceVisibility\": \"%s\" }".formatted(attendanceVisibility)))
                .andExpect(status().isOk());
    }

    private void attendWith(Users user, UUID eventId, String body) throws Exception {
        mockMvc.perform(post("/rest/events/" + eventId + "/attendance").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    private void assertMyAttendance(Users user, UUID eventId, boolean lookingForPartner) throws Exception {
        mockMvc.perform(get("/rest/events/" + eventId + "/attendance/me").with(as(user)))
                .andExpect(jsonPath("$.going").value(true))
                .andExpect(jsonPath("$.lookingForPartner").value(lookingForPartner));
    }
}
