package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 7: @username (elegirlo, comprobarlo mientras se escribe, abrir el perfil con el), contra Postgres real. */
class UsernameIntegrationTest extends IntegrationTest {

    @Test
    void aChosenUsernameOpensTheProfile() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        changeUsername(ana, "ana.baila").andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.username").value("ana.baila"));
        mockMvc.perform(get("/rest/users/by-username/ana.baila").with(as(luca)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ana.getId().toString()))
                .andExpect(jsonPath("$.username").value("ana.baila"))
                .andExpect(jsonPath("$.relation").value("NONE"));
        // Un link escrito a mano con mayusculas tambien
        mockMvc.perform(get("/rest/users/by-username/Ana.Baila").with(as(luca)))
                .andExpect(status().isOk());
    }

    /** El campo «Nome utente» mientras se escribe: el propio cuenta como libre. */
    @Test
    void availabilityExplainsWhyANameCannotBeChosen() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        changeUsername(luca, "luca").andExpect(status().isNoContent());

        expectAvailability(ana, "ana_b").andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()));
        expectAvailability(luca, "luca").andExpect(jsonPath("$.available").value(true));
        expectAvailability(ana, "luca").andExpect(jsonPath("$.reason").value("TAKEN"));
        expectAvailability(ana, "admin").andExpect(jsonPath("$.reason").value("RESERVED"));
        for (String invalid : new String[]{"Ana", "ab", ".ana", "ana.", "ana..b", "ana-b", "a".repeat(31)}) {
            expectAvailability(ana, invalid)
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.reason").value("INVALID"));
        }
    }

    @Test
    void aTakenReservedOrInvalidNameIsRejected() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        changeUsername(luca, "luca").andExpect(status().isNoContent());

        changeUsername(ana, "luca").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("USERNAME_TAKEN"));
        changeUsername(ana, "admin").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("USERNAME_RESERVED"));
        changeUsername(ana, "Ana").andExpect(status().isBadRequest());
        changeUsername(ana, "ana.").andExpect(status().isBadRequest());
        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.username").value(nullValue()));
    }

    /** El viejo queda libre en el momento: su link da 404 y otra persona lo puede elegir. Vacio lo quita. */
    @Test
    void changingTheUsernameFreesTheOldOne() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        changeUsername(ana, "ana.baila").andExpect(status().isNoContent());

        changeUsername(ana, "ana.salsa").andExpect(status().isNoContent());
        mockMvc.perform(get("/rest/users/by-username/ana.baila").with(as(luca)))
                .andExpect(status().isNotFound());
        changeUsername(luca, "ana.baila").andExpect(status().isNoContent());

        changeUsername(ana, "").andExpect(status().isNoContent());
        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.username").value(nullValue()));
        mockMvc.perform(get("/rest/users/by-username/ana.salsa").with(as(luca)))
                .andExpect(status().isNotFound());
    }

    /** El formulario completo de PATCH /auth/me no lo toca: un FE que no lo conoce no lo borra. */
    @Test
    void theFullProfileFormKeepsTheUsername() throws Exception {
        Users ana = createUser("Ana");
        changeUsername(ana, "ana.baila").andExpect(status().isNoContent());

        mockMvc.perform(patch("/rest/auth/me").with(as(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"showProfilePublic\": true, \"bio\": \"Ciao\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana.baila"));
    }

    /** Los mismos 404 que /users/{id}: privado, bloqueo, baneado. El dueño se ve igual. Un nombre de nadie, tambien. */
    @Test
    void aHiddenProfileIsNotFoundByUsername() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users marco = createUser("Marco");
        changeUsername(ana, "ana.baila").andExpect(status().isNoContent());
        changeUsername(marco, "marco").andExpect(status().isNoContent());

        // Con UPDATE y no con save(ana): el merge de la entidad de createUser pisaria el username con null
        jdbcTemplate.update("UPDATE users SET show_profile_public = FALSE WHERE id = ?", ana.getId());
        mockMvc.perform(get("/rest/users/by-username/ana.baila").with(as(luca))).andExpect(status().isNotFound());
        mockMvc.perform(get("/rest/users/by-username/ana.baila").with(as(ana))).andExpect(status().isOk());

        mockMvc.perform(post("/rest/users/" + luca.getId() + "/block").with(as(marco))).andExpect(status().isNoContent());
        mockMvc.perform(get("/rest/users/by-username/marco").with(as(luca))).andExpect(status().isNotFound());

        Users giulia = createUser("Giulia");
        changeUsername(giulia, "giulia").andExpect(status().isNoContent());
        jdbcTemplate.update("UPDATE users SET is_banned = TRUE, banned_at = now() WHERE id = ?", giulia.getId());
        mockMvc.perform(get("/rest/users/by-username/giulia").with(as(luca))).andExpect(status().isNotFound());

        mockMvc.perform(get("/rest/users/by-username/nessuno").with(as(luca))).andExpect(status().isNotFound());
    }

    private ResultActions changeUsername(Users user, String username) throws Exception {
        return mockMvc.perform(patch("/rest/users/me/username").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"%s\" }".formatted(username)));
    }

    private ResultActions expectAvailability(Users user, String username) throws Exception {
        return mockMvc.perform(get("/rest/users/username-available").param("u", username).with(as(user)))
                .andExpect(status().isOk());
    }
}
