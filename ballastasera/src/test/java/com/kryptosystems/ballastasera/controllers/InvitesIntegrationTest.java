package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.InvitePhrase;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Invita a ballare»: reglas, limite, aviso en la campanita, email y el dialogo con el estado de cada amigo. */
class InvitesIntegrationTest extends IntegrationTest {

    @Test
    void invitingAFriendNotifiesAndEmailsThem() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa al Tropical");
        attend(ana, eventId);

        invite(ana, luca, eventId).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("INVITE"))
                .andExpect(jsonPath("$.items[0].actor.userId").value(ana.getId().toString()))
                .andExpect(jsonPath("$.items[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.items[0].eventTitle").value("Salsa al Tropical"))
                .andExpect(jsonPath("$.items[0].phrase").value("CI_VIENI"));
        verify(emailService).sendInviteEmail(eq(luca.getEmail()), eq("Ana"), eq(eventId), eq("Salsa al Tropical"),
                any(), eq(InvitePhrase.CI_VIENI));
    }

    @Test
    void invitingTwiceIsIdempotent() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, luca, eventId).andExpect(status().isNoContent());
        invite(ana, luca, eventId).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items.length()").value(1));
        verify(emailService, times(1)).sendInviteEmail(anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void noEmailWhenTheFriendTurnedEmailsOff() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        luca.setEmailNotifications(false);
        save(luca);
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, luca, eventId).andExpect(status().isNoContent());

        verify(emailService, never()).sendInviteEmail(anyString(), anyString(), any(), anyString(), any(), any());
    }

    /** Silenciado: la invitacion no le sale en la campanita, asi que tampoco le llega por email. */
    @Test
    void noEmailWhenTheFriendMutedTheInviter() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        jdbcTemplate.update("UPDATE user_follows SET muted = TRUE WHERE follower_id = ? AND followed_id = ?",
                luca.getId(), ana.getId());
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, luca, eventId).andExpect(status().isNoContent());

        verify(emailService, never()).sendInviteEmail(anyString(), anyString(), any(), anyString(), any(), any());
        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void youCanOnlyInviteFriends() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        jdbcTemplate.update("INSERT INTO user_follows (follower_id, followed_id) VALUES (?, ?)", ana.getId(), luca.getId());
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, luca, eventId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("NOT_FRIENDS"));
    }

    @Test
    void youCannotInviteYourself() throws Exception {
        Users ana = createUser("Ana");
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, ana, eventId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("SELF_ACTION"));
    }

    /** Un perfil privado queda aislado: no invita... */
    @Test
    void aPrivateProfileCannotInvite() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        ana.setShowProfilePublic(false);
        save(ana);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, luca, eventId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("PROFILE_PRIVATE"));
        mockMvc.perform(get("/rest/events/" + eventId + "/invites/friends").with(as(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("PROFILE_PRIVATE"));
    }

    /** ...ni lo invitan. */
    @Test
    void aPrivateFriendCannotBeInvited() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        luca.setShowProfilePublic(false);
        save(luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, luca, eventId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("FRIEND_PRIVATE"));
    }

    @Test
    void youCanOnlyInviteToASerataYouAreGoingTo() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");

        invite(ana, luca, eventId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("NOT_GOING"));
    }

    @Test
    void endedOrCancelledSerateCannotBeInvitedTo() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID ended = createEventStartingIn("Salsa di ieri", "-1 day");
        UUID cancelled = createEvent("Salsa annullata");
        jdbcTemplate.update("UPDATE events SET status = 'CANCELLED' WHERE id = ?", cancelled);
        attend(ana, ended);
        attend(ana, cancelled);

        invite(ana, luca, ended)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("EVENT_NOT_INVITABLE"));
        invite(ana, luca, cancelled)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("EVENT_NOT_INVITABLE"));
    }

    @Test
    void aSerataThatStartedCanStillBeInvitedTo() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEventStartingIn("Salsa in corso", "-1 hour");
        attend(ana, eventId);

        invite(ana, luca, eventId).andExpect(status().isNoContent());
    }

    @Test
    void aFriendWhoIsAlreadyGoingCannotBeInvited() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        attend(luca, eventId);

        invite(ana, luca, eventId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("ALREADY_GOING"));
    }

    /** Si oculta sus serate a todos, rechazar la invitacion revelaria que va: se deja invitar. */
    @Test
    void aFriendHidingTheirSerateCanBeInvitedEvenIfGoing() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        luca.setAttendanceVisibility(ContactVisibility.NOBODY);
        save(luca);
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        attend(luca, eventId);

        invite(ana, luca, eventId).andExpect(status().isNoContent());
    }

    @Test
    void unknownOrBannedPeopleAreNotFound() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        invite(ana, UUID.randomUUID(), eventId).andExpect(status().isNotFound());
        jdbcTemplate.update("UPDATE users SET is_banned = TRUE WHERE id = ?", luca.getId());
        invite(ana, luca, eventId).andExpect(status().isNotFound());
    }

    @Test
    void theEleventhInviteIn24HoursIsRejected() throws Exception {
        Users ana = createUser("Ana");
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        for (int i = 0; i < 10; i++) {
            Users friend = createUser("Friend" + i);
            befriend(ana, friend);
            invite(ana, friend, eventId).andExpect(status().isNoContent());
        }
        Users last = createUser("Last");
        befriend(ana, last);

        invite(ana, last, eventId)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()))
                .andExpect(jsonPath("$.errors.code").value("INVITE_LIMIT"));
        mockMvc.perform(get("/rest/events/" + eventId + "/invites/friends").with(as(ana)))
                .andExpect(jsonPath("$.invitesLeft").value(0));
    }

    @Test
    void inviteBodyIsValidated() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        mockMvc.perform(post("/rest/users/" + luca.getId() + "/invites").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"phrase\": \"CI_VIENI\" }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/rest/users/" + luca.getId() + "/invites").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"eventId\": \"" + UUID.randomUUID() + "\", \"phrase\": \"VIENI_E_BASTA\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phrase").exists());
    }

    /** La invitacion a una serata que ya termino desaparece de la campanita. */
    @Test
    void invitesToEndedSerateLeaveTheBell() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        invite(ana, luca, eventId).andExpect(status().isNoContent());

        jdbcTemplate.update("UPDATE events SET start_at = now() - INTERVAL '1 day' WHERE id = ?", eventId);

        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(luca)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    /** Lo mismo si la serata se cancela: la otra rama del filtro de la campanita (status). */
    @Test
    void invitesToCancelledSerateLeaveTheBell() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        invite(ana, luca, eventId).andExpect(status().isNoContent());

        jdbcTemplate.update("UPDATE events SET status = 'CANCELLED' WHERE id = ?", eventId);

        mockMvc.perform(get("/rest/users/me/notifications").with(as(luca)))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/rest/users/me/notifications/unread-count").with(as(luca)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    /** El dialogo: cada amigo con su estado, y cuantas invitaciones quedan. */
    @Test
    void theDialogShowsTheStatusOfEachFriend() throws Exception {
        Users ana = createUser("Ana");
        Users invitable = createUser("Bea");
        Users going = createUser("Carla");
        Users hiddenGoing = createUser("Dario");
        Users invited = createUser("Elena");
        Users privateFriend = createUser("Franco");
        hiddenGoing.setAttendanceVisibility(ContactVisibility.NOBODY);
        save(hiddenGoing);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        attend(going, eventId);
        attend(hiddenGoing, eventId);
        for (Users friend : new Users[]{invitable, going, hiddenGoing, invited, privateFriend}) {
            befriend(ana, friend);
        }
        invite(ana, invited, eventId).andExpect(status().isNoContent());
        privateFriend.setShowProfilePublic(false);
        save(privateFriend);

        mockMvc.perform(get("/rest/events/" + eventId + "/invites/friends").with(as(ana)))
                .andExpect(jsonPath("$.invitesLeft").value(9))
                .andExpect(jsonPath("$.friends.total").value(5))
                .andExpect(statusOf(invitable, "INVITABLE"))
                .andExpect(statusOf(going, "ALREADY_GOING"))
                .andExpect(statusOf(hiddenGoing, "INVITABLE"))
                .andExpect(statusOf(invited, "ALREADY_INVITED"))
                .andExpect(statusOf(privateFriend, "PRIVATE"))
                .andExpect(jsonPath("$.friends.items[0].user.relation").value("MUTUAL"));
    }

    @Test
    void theDialogSearchesByName() throws Exception {
        Users ana = createUser("Ana");
        Users bea = createUser("Bea");
        Users carla = createUser("Carla");
        befriend(ana, bea);
        befriend(ana, carla);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);

        mockMvc.perform(get("/rest/events/" + eventId + "/invites/friends").param("q", "car").with(as(ana)))
                .andExpect(jsonPath("$.friends.items.length()").value(1))
                .andExpect(jsonPath("$.friends.items[0].user.userId").value(carla.getId().toString()));
    }

    /** El dialogo da los mismos 400 que invitar: no muestra amigos «invitabili» a una serata que no se puede. */
    @Test
    void theDialogRejectsSerateYouCannotInviteTo() throws Exception {
        Users ana = createUser("Ana");
        befriend(ana, createUser("Luca"));
        UUID notGoing = createEvent("Salsa");
        UUID ended = createEventStartingIn("Salsa di ieri", "-1 day");
        attend(ana, ended);

        mockMvc.perform(get("/rest/events/" + notGoing + "/invites/friends").with(as(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("NOT_GOING"));
        mockMvc.perform(get("/rest/events/" + ended + "/invites/friends").with(as(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("EVENT_NOT_INVITABLE"));
    }

    @Test
    void theDialogOfAMissingSerataIsNotFound() throws Exception {
        mockMvc.perform(get("/rest/events/" + UUID.randomUUID() + "/invites/friends").with(as(createUser("Ana"))))
                .andExpect(status().isNotFound());
    }

    /** Las invitaciones cuentan por las ultimas 24 horas: una de ayer ya no resta. */
    @Test
    void invitesOlderThan24HoursDoNotCount() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        befriend(ana, luca);
        UUID eventId = createEvent("Salsa");
        attend(ana, eventId);
        invite(ana, luca, eventId).andExpect(status().isNoContent());
        jdbcTemplate.update("UPDATE notifications SET created_at = now() - INTERVAL '25 hours' WHERE actor_id = ?",
                ana.getId());

        mockMvc.perform(get("/rest/events/" + eventId + "/invites/friends").with(as(ana)))
                .andExpect(jsonPath("$.invitesLeft").value(10));
    }

    private ResultActions invite(Users inviter, Users invitee, UUID eventId) throws Exception {
        return invite(inviter, invitee.getId(), eventId);
    }

    private ResultActions invite(Users inviter, UUID inviteeId, UUID eventId) throws Exception {
        return mockMvc.perform(post("/rest/users/" + inviteeId + "/invites").with(as(inviter))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"eventId\": \"" + eventId + "\", \"phrase\": \"CI_VIENI\" }"));
    }

    /** Amigos directo en la DB: sin pasar por /follow, asi no hay avisos de follow en la campanita. */
    private void befriend(Users userA, Users userB) {
        jdbcTemplate.update("INSERT INTO user_follows (follower_id, followed_id) VALUES (?, ?), (?, ?)",
                userA.getId(), userB.getId(), userB.getId(), userA.getId());
    }

    private static ResultMatcher statusOf(Users friend, String status) {
        return jsonPath("$.friends.items[?(@.user.userId == '" + friend.getId() + "')].status")
                .value(contains(status));
    }
}
