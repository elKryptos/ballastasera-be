package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Campos del perfil de la fase 2 (bio, escuela, ciudad, pais, quien ve tus serate), contra Postgres real. */
class ProfileIntegrationTest extends IntegrationTest {

    private static final String MILANO = """
            { "name": "Milano", "region": "Lombardia", "country": "IT", "latitude": 45.46, "longitude": 9.19 }""";

    @Test
    void theOwnerGetsBackEveryNewField() throws Exception {
        Users ana = createUser("Ana");

        mockMvc.perform(patch("/rest/auth/me").with(as(ana)).contentType(MediaType.APPLICATION_JSON).content("""
                        { "showProfilePublic": true, "bio": "  Bachata e salsa  ", "attendanceVisibility": "MUTUALS",
                          "schoolName": "Crew del Naviglio", "originCountry": "CO",
                          "homeCity": %s, "showHomeCity": true }
                        """.formatted(MILANO)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.bio").value("Bachata e salsa"))
                .andExpect(jsonPath("$.attendanceVisibility").value("MUTUALS"))
                .andExpect(jsonPath("$.schoolName").value("Crew del Naviglio"))
                .andExpect(jsonPath("$.schoolOrganizerId").value(nullValue()))
                .andExpect(jsonPath("$.originCountry").value("CO"))
                .andExpect(jsonPath("$.homeCity.name").value("Milano"))
                .andExpect(jsonPath("$.homeCity.region").value("Lombardia"))
                .andExpect(jsonPath("$.showHomeCity").value(true))
                .andExpect(jsonPath("$.avatarSource").value("GOOGLE"));
    }

    /** El perfil publico: la ciudad solo si el dueño la muestra, y solo el nombre. */
    @Test
    void theCityIsPublicOnlyWhenTheOwnerShowsIt() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        saveProfile(ana, """
                { "showProfilePublic": true, "bio": "Ciao", "originCountry": "VE", "homeCity": %s, "showHomeCity": false }
                """.formatted(MILANO));
        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca)))
                .andExpect(jsonPath("$.bio").value("Ciao"))
                .andExpect(jsonPath("$.originCountry").value("VE"))
                .andExpect(jsonPath("$.homeCityName").value(nullValue()))
                .andExpect(jsonPath("$.homeCity").doesNotExist());

        saveProfile(ana, """
                { "showProfilePublic": true, "homeCity": %s, "showHomeCity": true }
                """.formatted(MILANO));
        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(luca)))
                .andExpect(jsonPath("$.homeCityName").value("Milano"));
    }

    /** Formulario completo: lo que no viene se borra y los defaults vuelven (serate a todos, ciudad oculta). */
    @Test
    void aFormWithoutTheNewFieldsClearsThem() throws Exception {
        Users ana = createUser("Ana");
        saveProfile(ana, """
                { "showProfilePublic": true, "bio": "Ciao", "attendanceVisibility": "NOBODY",
                  "homeCity": %s, "showHomeCity": true }
                """.formatted(MILANO));

        saveProfile(ana, "{ \"showProfilePublic\": true }");

        mockMvc.perform(get("/rest/auth/me").with(as(ana)))
                .andExpect(jsonPath("$.bio").value(nullValue()))
                .andExpect(jsonPath("$.attendanceVisibility").value("EVERYONE"))
                .andExpect(jsonPath("$.homeCity").value(nullValue()))
                .andExpect(jsonPath("$.showHomeCity").value(false));
    }

    @Test
    void aLinkedSchoolShowsItsNameAndSlug() throws Exception {
        Users ana = createUser("Ana");
        UUID schoolId = createOrganizer("Escuela Tropical", "SCHOOL");

        saveProfile(ana, """
                { "showProfilePublic": true, "schoolOrganizerId": "%s", "schoolName": "ignorado" }
                """.formatted(schoolId));

        mockMvc.perform(get("/rest/users/" + ana.getId()).with(as(createUser("Luca"))))
                .andExpect(jsonPath("$.schoolOrganizerId").value(schoolId.toString()))
                .andExpect(jsonPath("$.schoolName").value("Escuela Tropical"))
                .andExpect(jsonPath("$.schoolSlug").exists());
    }

    @Test
    void onlyASchoolCanBeLinked() throws Exception {
        Users ana = createUser("Ana");
        UUID clubId = createOrganizer("Club Latino", "CLUB");

        mockMvc.perform(patch("/rest/auth/me").with(as(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"showProfilePublic\": true, \"schoolOrganizerId\": \"%s\" }".formatted(clubId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidProfileFieldsAreRejected() throws Exception {
        Users ana = createUser("Ana");

        mockMvc.perform(patch("/rest/auth/me").with(as(ana)).contentType(MediaType.APPLICATION_JSON).content("""
                        { "showProfilePublic": true, "bio": "%s", "originCountry": "Colombia",
                          "homeCity": { "name": "Milano", "country": "Italia", "latitude": 95, "longitude": 9.19 } }
                        """.formatted("x".repeat(151))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bio").exists())
                .andExpect(jsonPath("$.errors.originCountry").exists())
                .andExpect(jsonPath("$.errors['homeCity.country']").exists())
                .andExpect(jsonPath("$.errors['homeCity.latitude']").exists());
    }

    /** Buscador de «Scuola o crew»: solo escuelas, sin distinguir mayusculas. */
    @Test
    void schoolSearchFindsOnlySchools() throws Exception {
        createOrganizer("Tropical Dance School", "SCHOOL");
        createOrganizer("Tropical Club", "CLUB");

        mockMvc.perform(get("/rest/organizers/schools").param("name", "tropi"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Tropical Dance School"));
    }

    /** «Chi ci va»: NOBODY no sale para nadie, MUTUALS solo para amigos, y cada uno se ve a si mismo. */
    @Test
    void chiCiVaRespectsWhoSeesYourSerate() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        Users marco = createUser("Marco");
        UUID eventId = createEvent("Bachata Night");
        attend(luca, eventId);
        attend(sara, eventId);
        saveProfile(luca, "{ \"showProfilePublic\": true, \"attendanceVisibility\": \"MUTUALS\" }");
        saveProfile(sara, "{ \"showProfilePublic\": true, \"attendanceVisibility\": \"NOBODY\" }");
        follow(ana, luca);
        follow(luca, ana);
        follow(marco, luca);

        mockMvc.perform(get("/rest/events/" + eventId + "/attendees").with(as(ana)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Luca"));
        mockMvc.perform(get("/rest/events/" + eventId + "/attendees").with(as(marco)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/rest/events/" + eventId + "/attendees"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/rest/events/" + eventId + "/attendees").with(as(sara)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Sara"));
    }

    private void saveProfile(Users user, String json) throws Exception {
        mockMvc.perform(patch("/rest/auth/me").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void follow(Users follower, Users followed) throws Exception {
        mockMvc.perform(post("/rest/users/" + followed.getId() + "/follow").with(as(follower)))
                .andExpect(status().isNoContent());
    }
}
