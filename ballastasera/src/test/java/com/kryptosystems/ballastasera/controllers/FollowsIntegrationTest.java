package com.kryptosystems.ballastasera.controllers;

import com.jayway.jsonpath.JsonPath;
import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seguir, relacion, contadores y listas, contra Postgres real. */
class FollowsIntegrationTest extends IntegrationTest {

    @Test
    void followingChangesTheRelationOnBothProfiles() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.relation").value("NONE"));

        follow(ana, luca);
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.relation").value("FOLLOWING"))
                .andExpect(jsonPath("$.followersCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(0));
        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca)))
                .andExpect(jsonPath("$.relation").value("FOLLOWED_BY"));

        follow(luca, ana);
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.relation").value("MUTUAL"));
    }

    @Test
    void followIsIdempotent() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        follow(ana, luca);
        follow(ana, luca);

        mockMvc.perform(get("/rest/auth/me").with(as(luca)))
                .andExpect(jsonPath("$.followersCount").value(1));
    }

    @Test
    void youCannotFollowYourself() throws Exception {
        Users ana = createUser("Ana");

        mockMvc.perform(post("/rest/users/" + ana.getId() + "/follow").with(as(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("SELF_ACTION"));
    }

    @Test
    void aPrivateProfileCannotBeFollowed() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        luca.setShowProfilePublic(false);
        save(luca);

        mockMvc.perform(post("/rest/users/" + luca.getId() + "/follow").with(as(ana)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unfollowIsIdempotent() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);

        mockMvc.perform(delete("/rest/users/" + luca.getId() + "/follow").with(as(ana))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/rest/users/" + luca.getId() + "/follow").with(as(ana))).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/me/following").with(as(ana)))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    /** «Solo amici» (el default de los nuevos): seguirlo no basta, tiene que seguirte tambien. */
    @Test
    void aFriendsOnlyWhatsappShowsUpWhenTheFollowIsMutual() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        luca.setWhatsapp("+393331234567");
        save(luca);

        follow(ana, luca);
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.whatsapp").value(nullValue()));

        follow(luca, ana);
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.whatsapp").value("+393331234567"));
    }

    @Test
    void newUsersStartWithFriendsOnlyWhatsapp() throws Exception {
        Users ana = createUser("Ana");

        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.whatsappVisibility").value(ContactVisibility.MUTUALS.name()));
    }

    /** Cada fila trae la relacion con quien pide la lista: Luca me sigue y yo a el (MUTUAL), Sara solo me sigue. */
    @Test
    void followersCarryTheRelationOfEachRow() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        follow(luca, ana);
        follow(sara, ana);
        follow(ana, luca);

        mockMvc.perform(get("/rest/users/me/followers").with(as(ana)))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.displayName == 'Luca')].relation").value(contains("MUTUAL")))
                .andExpect(jsonPath("$.items[?(@.displayName == 'Sara')].relation").value(contains("FOLLOWED_BY")));
    }

    @Test
    void followingListsTheMostRecentFirstWithItsRelation() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        follow(ana, luca);
        follow(ana, sara);
        follow(sara, ana);

        mockMvc.perform(get("/rest/users/me/following").with(as(ana)))
                .andExpect(jsonPath("$.items[0].displayName").value("Sara"))
                .andExpect(jsonPath("$.items[0].relation").value("MUTUAL"))
                .andExpect(jsonPath("$.items[1].displayName").value("Luca"))
                .andExpect(jsonPath("$.items[1].relation").value("FOLLOWING"));
    }

    /** Amigos = solo los mutuos. q busca sin distinguir mayusculas y un % se busca tal cual. */
    @Test
    void friendsAreOnlyMutualFollowsAndCanBeSearched() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users lucia = createUser("Lucia");
        Users sara = createUser("Sara");
        follow(ana, luca);
        follow(luca, ana);
        follow(ana, lucia);
        follow(lucia, ana);
        follow(ana, sara);

        mockMvc.perform(get("/rest/users/me/friends").with(as(ana)))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/rest/users/me/friends").param("q", "LUCI").with(as(ana)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Lucia"))
                .andExpect(jsonPath("$.items[0].relation").value("MUTUAL"));
        mockMvc.perform(get("/rest/users/me/friends").param("q", "%").with(as(ana)))
                .andExpect(jsonPath("$.total").value(0));
    }

    /** 3 seguidores de a 2: la segunda pagina, con el cursor de la primera, trae el que falta y no repite. */
    @Test
    void followersArePagedWithACursor() throws Exception {
        Users ana = createUser("Ana");
        follow(createUser("Luca"), ana);
        follow(createUser("Sara"), ana);
        follow(createUser("Marco"), ana);

        String firstPage = mockMvc.perform(get("/rest/users/me/followers").param("limit", "2").with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].displayName").value("Marco"))
                .andExpect(jsonPath("$.nextCursorId").exists())
                .andReturn().getResponse().getContentAsString();
        String cursorStartAt = JsonPath.read(firstPage, "$.nextCursorStartAt");
        String cursorId = JsonPath.read(firstPage, "$.nextCursorId");

        mockMvc.perform(get("/rest/users/me/followers").param("limit", "2")
                        .param("cursorStartAt", cursorStartAt).param("cursorId", cursorId).with(as(ana)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Luca"))
                .andExpect(jsonPath("$.nextCursorId").value(nullValue()));
    }

    /** Silenciar sale solo en el perfil de quien silencia: el silenciado no se entera. */
    @Test
    void mutingShowsOnlyOnTheProfileOfWhoMuted() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);
        follow(luca, ana);

        mute(ana, luca, true).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.muted").value(true))
                .andExpect(jsonPath("$.relation").value("MUTUAL"));
        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca)))
                .andExpect(jsonPath("$.muted").value(false));

        mute(ana, luca, false).andExpect(status().isNoContent());
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.muted").value(false));
    }

    /** Solo se silencia a quien sigues; y dejar de seguir pierde el silencio con la fila. */
    @Test
    void youCanOnlyMuteSomeoneYouFollow() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        mute(ana, luca, true).andExpect(status().isNotFound());

        follow(ana, luca);
        mute(ana, luca, true).andExpect(status().isNoContent());
        mockMvc.perform(delete("/rest/users/" + luca.getId() + "/follow").with(as(ana))).andExpect(status().isNoContent());
        follow(ana, luca);
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(jsonPath("$.muted").value(false));
    }

    @Test
    void muteNeedsABody() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        follow(ana, luca);

        mockMvc.perform(patch("/rest/users/" + luca.getId() + "/follow").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void follow(Users follower, Users followed) throws Exception {
        mockMvc.perform(post("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }

    private ResultActions mute(Users follower, Users followed, boolean muted) throws Exception {
        return mockMvc.perform(patch("/rest/users/" + followed.getId() + "/follow").with(as(follower))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"muted\": " + muted + " }"));
    }
}
