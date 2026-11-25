package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pestaña Amici: feed y sugerencias, contra Postgres real. Las serate de «stasera» empiezan hace una hora (en curso):
 * una «dentro de 2 horas» caeria fuera de esta noche si el test corre entre las 04:00 y las 06:00.
 */
class FeedIntegrationTest extends IntegrationTest {

    @Test
    void tonightGroupsThePeopleIFollowByEvent() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        Users marco = createUser("Marco");
        follow(ana, luca);
        follow(ana, sara);
        follow(sara, ana);
        UUID salsa = createEventStartingIn("Serata salsa", "-1 hours");
        attend(luca, salsa);
        attend(sara, salsa);
        attend(marco, salsa);
        attend(ana, salsa);

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tonight.length()").value(1))
                .andExpect(jsonPath("$.tonight[0].eventTitle").value("Serata salsa"))
                .andExpect(jsonPath("$.tonight[0].meGoing").value(true))
                .andExpect(jsonPath("$.tonight[0].people.length()").value(2))
                .andExpect(jsonPath("$.tonight[0].people[0].displayName").value("Luca"))
                .andExpect(jsonPath("$.tonight[0].people[0].relation").value("FOLLOWING"))
                .andExpect(jsonPath("$.tonight[0].people[1].displayName").value("Sara"))
                .andExpect(jsonPath("$.tonight[0].people[1].relation").value("MUTUAL"));
    }

    /** Una serata de mañana no es «stasera»: sale en «Novità» como «va a X». */
    @Test
    void anUpcomingEventIsNewsNotTonight() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        UUID bachata = createEventStartingIn("Bachata Sunday", "3 days");
        attend(luca, bachata);

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.tonight").isEmpty())
                .andExpect(jsonPath("$.news.length()").value(1))
                .andExpect(jsonPath("$.news[0].type").value("GOING_TO"))
                .andExpect(jsonPath("$.news[0].user.displayName").value("Luca"))
                .andExpect(jsonPath("$.news[0].eventTitle").value("Bachata Sunday"))
                .andExpect(jsonPath("$.news[0].meGoing").value(false));
    }

    /** «ti segue» con «Ricambia» (FOLLOWED_BY); al seguirlo de vuelta pasa a MUTUAL. */
    @Test
    void aNewFollowerIsNewsWithTheRightButton() throws Exception {
        Users ana = createUser("Ana");
        Users chiara = createUser("Chiara");
        follow(chiara, ana);

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.news.length()").value(1))
                .andExpect(jsonPath("$.news[0].type").value("FOLLOWED_YOU"))
                .andExpect(jsonPath("$.news[0].user.displayName").value("Chiara"))
                .andExpect(jsonPath("$.news[0].user.relation").value("FOLLOWED_BY"))
                .andExpect(jsonPath("$.news[0].eventId").doesNotExist());

        follow(ana, chiara);

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.news[?(@.type == 'FOLLOWED_YOU')].user.relation").value("MUTUAL"));
    }

    /** Alguien privado que me sigue sale en «ti segue», pero con profilePublic false: sin enlace ni «Ricambia». */
    @Test
    void aPrivateFollowerIsNewsWithoutTheButton() throws Exception {
        Users ana = createUser("Ana");
        Users hidden = createUser("Hidden");
        hidden.setShowProfilePublic(false);
        save(hidden);
        follow(hidden, ana);

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.news.length()").value(1))
                .andExpect(jsonPath("$.news[0].user.displayName").value("Hidden"))
                .andExpect(jsonPath("$.news[0].user.profilePublic").value(false));
    }

    /** Bloquear borra los follows en los dos sentidos: desaparece de «Stasera escono» y de «ti segue». */
    @Test
    void aBlockRemovesThePersonFromTheFeed() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        follow(luca, ana);
        UUID salsa = createEventStartingIn("Serata salsa", "-1 hours");
        attend(luca, salsa);
        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.tonight.length()").value(1))
                .andExpect(jsonPath("$.news.length()").value(1));

        mockMvc.perform(post("/rest/users/" + luca.getId() + "/block").with(as(ana)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.tonight").isEmpty())
                .andExpect(jsonPath("$.news").isEmpty());
    }

    /** Silenciado no sale; MUTUALS solo si es amigo; NOBODY nunca. */
    @Test
    void theFeedRespectsMutesAndAttendanceVisibility() throws Exception {
        Users ana = createUser("Ana");
        Users muted = createUser("Muted");
        Users friendsOnly = createUser("FriendsOnly");
        Users fan = createUser("Fan");
        Users nobody = createUser("Nobody");
        friendsOnly.setAttendanceVisibility(ContactVisibility.MUTUALS);
        fan.setAttendanceVisibility(ContactVisibility.MUTUALS);
        nobody.setAttendanceVisibility(ContactVisibility.NOBODY);
        save(friendsOnly);
        save(fan);
        save(nobody);
        follow(ana, muted);
        follow(ana, friendsOnly);
        follow(friendsOnly, ana);
        follow(ana, fan);
        follow(ana, nobody);
        mute(ana, muted);
        UUID salsa = createEventStartingIn("Serata salsa", "-1 hours");
        attend(muted, salsa);
        attend(friendsOnly, salsa);
        attend(fan, salsa);
        attend(nobody, salsa);

        mockMvc.perform(get("/rest/users/me/feed").with(as(ana)))
                .andExpect(jsonPath("$.tonight[0].people.length()").value(1))
                .andExpect(jsonPath("$.tonight[0].people[0].displayName").value("FriendsOnly"));
    }

    /** Primero quien comparte serata (con «Cerco qualcuno»), despues el amigo de un amigo. */
    @Test
    void suggestionsComeFromMyEventsAndFriendsOfFriends() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        Users noemi = createUser("Noemi");
        follow(ana, luca);
        follow(luca, ana);
        follow(luca, sara);
        follow(sara, luca);
        UUID noche = createEventStartingIn("Noche Latina", "2 days");
        attend(ana, noche);
        attend(noemi, noche);
        jdbcTemplate.update("UPDATE event_attendance SET looking_for_partner = TRUE WHERE user_id = ? AND event_id = ?",
                noemi.getId(), noche);
        follow(noemi, ana);

        mockMvc.perform(get("/rest/users/me/suggestions").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user.displayName").value("Noemi"))
                .andExpect(jsonPath("$[0].user.relation").value("FOLLOWED_BY"))
                .andExpect(jsonPath("$[0].eventTitle").value("Noche Latina"))
                .andExpect(jsonPath("$[0].lookingForPartner").value(true))
                .andExpect(jsonPath("$[1].user.displayName").value("Sara"))
                .andExpect(jsonPath("$[1].user.relation").value("NONE"))
                .andExpect(jsonPath("$[1].mutualFriendsCount").value(1))
                .andExpect(jsonPath("$[1].eventId").doesNotExist());
    }

    /** Nunca: a quien ya sigo, bloqueados, menores, perfiles privados, ni quien no muestra sus serate a todos. */
    @Test
    void suggestionsLeaveOutWhoShouldNotBeThere() throws Exception {
        Users ana = createUser("Ana");
        Users followed = createUser("Followed");
        Users blocked = createUser("Blocked");
        Users minor = createUser("Minor");
        Users hidden = createUser("Hidden");
        Users friendsOnly = createUser("FriendsOnly");
        minor.setBirthDate(LocalDate.now().minusYears(16));
        hidden.setShowProfilePublic(false);
        friendsOnly.setAttendanceVisibility(ContactVisibility.MUTUALS);
        save(minor);
        save(hidden);
        save(friendsOnly);
        follow(ana, followed);
        UUID noche = createEventStartingIn("Noche Latina", "2 days");
        attend(ana, noche);
        for (Users person : new Users[]{followed, blocked, minor, hidden, friendsOnly}) {
            attend(person, noche);
        }
        mockMvc.perform(post("/rest/users/" + blocked.getId() + "/block").with(as(ana)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/suggestions").with(as(ana)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void suggestionsLimitIsCapped() throws Exception {
        mockMvc.perform(get("/rest/users/me/suggestions").param("limit", "21").with(as(createUser("Ana"))))
                .andExpect(status().isBadRequest());
    }

    private void follow(Users follower, Users followed) throws Exception {
        mockMvc.perform(post("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }

    private void mute(Users follower, Users followed) throws Exception {
        mockMvc.perform(patch("/rest/users/" + followed.getId() + "/follow").with(as(follower))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"muted\": true }"))
                .andExpect(status().isNoContent());
    }
}
