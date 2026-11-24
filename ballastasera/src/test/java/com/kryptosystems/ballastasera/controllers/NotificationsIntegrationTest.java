package com.kryptosystems.ballastasera.controllers;

import com.jayway.jsonpath.JsonPath;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.services.implementations.NotificationsCleanupJob;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Campanita: «ti segue», «ora siete amici», sin repetidos, filtros al leer, leidos, email y limpieza. */
class NotificationsIntegrationTest extends IntegrationTest {

    @Autowired
    private NotificationsCleanupJob notificationsCleanupJob;

    /** Ana sigue a Luca: «ti segue» para Luca. Luca la sigue de vuelta: «ora siete amici» para Ana, con email. */
    @Test
    void followingNotifiesAndFollowingBackMakesFriends() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        follow(ana, luca);
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NEW_FOLLOWER"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].actor.userId").value(ana.getId().toString()))
                .andExpect(jsonPath("$.items[0].actor.relation").value("FOLLOWED_BY"))
                .andExpect(jsonPath("$.total").value(1));
        verify(emailService, never()).sendNewFriendEmail(anyString(), any(), anyString());

        follow(luca, ana);
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NEW_FRIEND"))
                .andExpect(jsonPath("$.items[0].actor.userId").value(luca.getId().toString()))
                .andExpect(jsonPath("$.items[0].actor.relation").value("MUTUAL"));
        verify(emailService).sendNewFriendEmail(ana.getEmail(), luca.getId(), "Luca");

        // Luca cerro la amistad: no recibe nada mas, y su «ti segue» ahora dice MUTUAL (ya no «Ricambia»)
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actor.relation").value("MUTUAL"));
    }

    @Test
    void withEmailsOffTheBellStillShowsTheNewFriend() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        mockMvc.perform(patch("/rest/users/me/email-notifications").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"enabled\": false }"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.emailNotifications").value(false));

        follow(ana, luca);
        follow(luca, ana);

        verify(emailService, never()).sendNewFriendEmail(anyString(), any(), anyString());
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items[0].type").value("NEW_FRIEND"));
    }

    /** Ana sigue a Luca y lo silencio: cuando Luca la sigue de vuelta, el aviso no le sale y el email tampoco. */
    @Test
    void noFriendEmailWhenTheNewFriendIsMuted() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        mute(ana, luca, true);

        follow(luca, ana);

        verify(emailService, never()).sendNewFriendEmail(anyString(), any(), anyString());
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void newUsersGetEmails() throws Exception {
        mockMvc.perform(get("/rest/auth/me").with(as(createUser("Ana"))))
                .andExpect(jsonPath("$.emailNotifications").value(true));
    }

    @Test
    void theEmailToggleNeedsABody() throws Exception {
        mockMvc.perform(patch("/rest/users/me/email-notifications").with(as(createUser("Ana")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** Seguir y dejar de seguir no llena la campanita: el aviso sin leer se va, y uno ya leido no se repite. */
    @Test
    void followingAgainDoesNotRepeatTheNotification() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        follow(ana, luca);
        unfollow(ana, luca);
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items").isEmpty());

        follow(ana, luca);
        markRead(luca);
        unfollow(ana, luca);
        follow(ana, luca);
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].read").value(true));
        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(luca)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    /** Luca ya sigue a Ana. Ana lo sigue, lo deja y lo vuelve a seguir: un solo «ora siete amici» y un solo email. */
    @Test
    void followingInALoopSendsTheFriendEmailOnce() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(luca, ana);

        for (int i = 0; i < 3; i++) {
            follow(ana, luca);
            unfollow(ana, luca);
        }
        follow(ana, luca);

        verify(emailService, times(1)).sendNewFriendEmail(luca.getEmail(), ana.getId(), "Ana");
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NEW_FRIEND"));
    }

    @Test
    void markingAsReadClearsTheCounter() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        follow(luca, ana);
        follow(sara, ana);

        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(ana)))
                .andExpect(jsonPath("$.unread").value(2));
        markRead(ana);
        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(ana)))
                .andExpect(jsonPath("$.unread").value(0));
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items[*].read").value(contains(true, true)));
    }

    /** Silenciar saca sus avisos de la lista y del contador; reactivarlo los devuelve. */
    @Test
    void mutedPeopleAreHiddenUntilUnmuted() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        follow(luca, ana);

        mute(ana, luca, true);
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(ana)))
                .andExpect(jsonPath("$.unread").value(0));

        mute(ana, luca, false);
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void blockingDeletesTheNotificationsBothWays() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        follow(luca, ana);

        mockMvc.perform(post("/rest/users/" + luca.getId() + "/block").with(as(ana)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void notificationsFromBannedUsersAreHidden() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(luca, ana);

        jdbcTemplate.update("UPDATE users SET is_banned = TRUE WHERE id = ?", luca.getId());

        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(ana)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    /** Decision a (24/11): el «ti segue» de alguien privado sale, sin enlace y con «Profilo privato» en el FE. */
    @Test
    void aPrivateFollowerShowsUpAsPrivate() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        luca.setShowProfilePublic(false);
        save(luca);

        follow(luca, ana);

        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items[0].type").value("NEW_FOLLOWER"))
                .andExpect(jsonPath("$.items[0].actor.profilePublic").value(false));
    }

    /** Si quien devuelve el follow es privado, solo campanita: el boton del email llevaria a un perfil que da 404. */
    @Test
    void aPrivateFriendDoesNotTriggerTheEmail() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        luca.setShowProfilePublic(false);
        save(luca);

        follow(luca, ana);

        verify(emailService, never()).sendNewFriendEmail(anyString(), any(), anyString());
        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items[0].type").value("NEW_FRIEND"))
                .andExpect(jsonPath("$.items[0].actor.profilePublic").value(false));
    }

    @Test
    void notificationsArePagedWithACursor() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        follow(luca, ana);
        follow(sara, ana);

        String firstPage = mockMvc.perform(get("/rest/users/me/notifications").param("limit", "1").with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actor.userId").value(sara.getId().toString()))
                .andExpect(jsonPath("$.total").value(2))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/rest/users/me/notifications").param("limit", "1")
                        .param("cursorStartAt", JsonPath.<String>read(firstPage, "$.nextCursorStartAt"))
                        .param("cursorId", JsonPath.<String>read(firstPage, "$.nextCursorId"))
                        .with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actor.userId").value(luca.getId().toString()))
                .andExpect(jsonPath("$.nextCursorId").doesNotExist());
    }

    @Test
    void notificationsOlderThan60DaysAreDeleted() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        follow(luca, ana);
        follow(sara, ana);
        jdbcTemplate.update("UPDATE notifications SET created_at = now() - INTERVAL '61 days' WHERE actor_id = ?",
                luca.getId());

        notificationsCleanupJob.deleteOldNotifications();

        mockMvc.perform(get("/rest/users/me/notifications").with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actor.userId").value(sara.getId().toString()));
    }

    private void follow(Users follower, Users followed) throws Exception {
        mockMvc.perform(post("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }

    private void unfollow(Users follower, Users followed) throws Exception {
        mockMvc.perform(delete("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }

    private void mute(Users follower, Users followed, boolean muted) throws Exception {
        mockMvc.perform(patch("/rest/users/" + followed.getId() + "/follow").with(as(follower))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"muted\": " + muted + " }"))
                .andExpect(status().isNoContent());
    }

    private void markRead(Users user) throws Exception {
        mockMvc.perform(patch("/rest/users/me/notifications/read").with(as(user)))
                .andExpect(status().isNoContent());
    }
}
