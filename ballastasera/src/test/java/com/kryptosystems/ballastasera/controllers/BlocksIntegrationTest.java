package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un test por cada sitio donde un bloqueo tiene que esconder a la otra persona. */
class BlocksIntegrationTest extends IntegrationTest {

    @Test
    void blockingRemovesTheFollowsInBothDirections() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        follow(luca, ana);

        block(ana, luca);

        mockMvc.perform(get("/rest/users/me/following").with(as(ana))).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/rest/users/me/following").with(as(luca))).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/rest/auth/me").with(as(ana))).andExpect(jsonPath("$.followersCount").value(0));
    }

    /** Mismo 404 que un perfil privado, en los dos sentidos: el bloqueado no sabe que lo bloquearon. */
    @Test
    void aBlockHidesTheProfileFromBothSides() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        block(ana, luca);

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana))).andExpect(status().isNotFound());
        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca))).andExpect(status().isNotFound());
    }

    @Test
    void aBlockedUserCannotFollowNorBeFollowed() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        block(ana, luca);

        mockMvc.perform(post("/rest/users/" + ana.getId() + "/follow").with(as(luca))).andExpect(status().isNotFound());
        mockMvc.perform(post("/rest/users/" + luca.getId() + "/follow").with(as(ana))).andExpect(status().isNotFound());
    }

    @Test
    void aBlockHidesTheOtherFromChiCiVaOnlyForThem() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        UUID eventId = createEvent("Salsa al Tropical");
        attend(luca, eventId);
        attend(sara, eventId);

        block(luca, ana);

        mockMvc.perform(get("/rest/events/" + eventId + "/attendees").with(as(ana)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Sara"));
        mockMvc.perform(get("/rest/events/" + eventId + "/attendees").with(as(sara)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/rest/events/" + eventId + "/attendees"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    /** Desbloquear devuelve el perfil, pero los follows borrados al bloquear no vuelven. */
    @Test
    void unblockingShowsTheProfileAgainWithoutTheOldFollows() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        block(ana, luca);

        mockMvc.perform(get("/rest/users/me/blocks").with(as(ana)))
                .andExpect(jsonPath("$[0].displayName").value("Luca"))
                .andExpect(jsonPath("$[0].relation").value("NONE"));

        mockMvc.perform(delete("/rest/users/" + luca.getId() + "/block").with(as(ana))).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/blocks").with(as(ana))).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relation").value("NONE"));
    }

    @Test
    void youCannotBlockYourself() throws Exception {
        Users ana = createUser("Ana");

        mockMvc.perform(post("/rest/users/" + ana.getId() + "/block").with(as(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("SELF_ACTION"));
    }

    /** Se puede bloquear a alguien con perfil privado (ej. alguien privado que te sigue). */
    @Test
    void aPrivateProfileCanBeBlocked() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        luca.setShowProfilePublic(false);
        save(luca);

        block(ana, luca);
    }

    private void follow(Users follower, Users followed) throws Exception {
        mockMvc.perform(post("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }

    private void block(Users blocker, Users blocked) throws Exception {
        mockMvc.perform(post("/rest/users/" + blocked.getId() + "/block").with(as(blocker)))
                .andExpect(status().isNoContent());
    }
}
