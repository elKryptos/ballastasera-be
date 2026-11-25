package com.kryptosystems.ballastasera.controllers;

import com.jayway.jsonpath.JsonPath;
import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Denuncias, cola del admin, ban y unban, contra Postgres real. */
class ModerationIntegrationTest extends IntegrationTest {

    private static final String FAKE_PROFILE = """
            { "reason": "FAKE_PROFILE" }
            """;

    /** Denunciar dos veces mientras la primera sigue abierta no crea otra. */
    @Test
    void reportingTwiceKeepsOneOpenReport() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users admin = createAdmin();

        report(ana, luca, FAKE_PROFILE);
        report(ana, luca, FAKE_PROFILE);

        mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reporterName").value("Ana"))
                .andExpect(jsonPath("$.content[0].reportedName").value("Luca"))
                .andExpect(jsonPath("$.content[0].reason").value("FAKE_PROFILE"))
                .andExpect(jsonPath("$.content[0].openReportsAgainst").value(1))
                .andExpect(jsonPath("$.content[0].reviewedByName").value(nullValue()));
    }

    @Test
    void otherNeedsANote() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");

        mockMvc.perform(post("/rest/users/" + luca.getId() + "/report").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "reason": "OTHER", "note": "  " }
                                """))
                .andExpect(status().isBadRequest());

        report(ana, luca, """
                { "reason": "OTHER", "note": "  Mi scrive di continuo  " }
                """);
        mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(createAdmin())))
                .andExpect(jsonPath("$.content[0].note").value("Mi scrive di continuo"));
    }

    @Test
    void youCannotReportYourself() throws Exception {
        Users ana = createUser("Ana");

        mockMvc.perform(post("/rest/users/" + ana.getId() + "/report").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(FAKE_PROFILE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("SELF_ACTION"));
    }

    /** Se puede denunciar a alguien privado (ej. te sigue y molesta), y la denuncia sola no oculta nada. */
    @Test
    void aPrivateProfileCanBeReportedAndAReportHidesNothing() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users sara = createUser("Sara");
        sara.setShowProfilePublic(false);
        save(sara);

        report(ana, sara, FAKE_PROFILE);
        report(ana, luca, FAKE_PROFILE);

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana))).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsSeeTheQueue() throws Exception {
        mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(createUser("Ana"))))
                .andExpect(status().isForbidden());
    }

    /** «Ignora» cierra solo esa denuncia: la de otra persona contra el mismo usuario sigue abierta. */
    @Test
    void dismissingClosesOnlyThatReport() throws Exception {
        Users luca = createUser("Luca");
        Users admin = createAdmin();
        report(createUser("Ana"), luca, FAKE_PROFILE);
        report(createUser("Sara"), luca, FAKE_PROFILE);
        String reportId = firstOpenReportId(admin);

        review(admin, reportId, "DISMISSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.reviewedByName").value("Admin"))
                .andExpect(jsonPath("$.openReportsAgainst").value(1))
                .andExpect(jsonPath("$.reportedBanned").value(false));

        mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(createUser("Marco")))).andExpect(status().isOk());
    }

    /** «Banna» cierra todas las abiertas contra esa persona y la esconde del resto. */
    @Test
    void banningFromAReportClosesAllOpenReportsAndHidesTheUser() throws Exception {
        Users luca = createUser("Luca");
        Users admin = createAdmin();
        report(createUser("Ana"), luca, FAKE_PROFILE);
        report(createUser("Sara"), luca, FAKE_PROFILE);

        review(admin, firstOpenReportId(admin), "BANNED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BANNED"))
                .andExpect(jsonPath("$.reportedBanned").value(true))
                .andExpect(jsonPath("$.openReportsAgainst").value(0));

        mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/rest/admin/reports").param("status", "BANNED").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(createUser("Marco")))).andExpect(status().isNotFound());
    }

    @Test
    void aReviewedReportCannotBeReviewedAgain() throws Exception {
        Users admin = createAdmin();
        report(createUser("Ana"), createUser("Luca"), FAKE_PROFILE);
        String reportId = firstOpenReportId(admin);
        review(admin, reportId, "DISMISSED").andExpect(status().isOk());

        review(admin, reportId, "BANNED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("REPORT_NOT_OPEN"));
    }

    @Test
    void openIsNotAValidReview() throws Exception {
        Users admin = createAdmin();
        report(createUser("Ana"), createUser("Luca"), FAKE_PROFILE);

        review(admin, firstOpenReportId(admin), "OPEN").andExpect(status().isBadRequest());
    }

    /** El JWT que ya tenia deja de valer en la siguiente request, sin esperar a que caduque. */
    @Test
    void aBannedUserLosesTheSessionTheyHad() throws Exception {
        Users luca = createUser("Luca");
        RequestPostProcessor session = as(luca);
        mockMvc.perform(get("/rest/auth/me").with(session)).andExpect(status().isOk());

        ban(createAdmin(), luca).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/auth/me").with(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCannotBeBanned() throws Exception {
        Users admin = createAdmin();

        ban(admin, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.code").value("CANNOT_BAN_ADMIN"));
    }

    /** Un ban directo tambien cierra sus denuncias abiertas, y sale en «Utenti bannati». */
    @Test
    void aDirectBanClosesOpenReportsAndListsTheUser() throws Exception {
        Users luca = createUser("Luca");
        Users admin = createAdmin();
        report(createUser("Ana"), luca, FAKE_PROFILE);

        ban(admin, luca).andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/rest/admin/users/banned").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Luca"))
                .andExpect(jsonPath("$.content[0].bannedAt").exists());
    }

    /** El ban no borra los follows: al quitarlo, perfil y contadores vuelven como estaban. */
    @Test
    void unbanningRestoresTheProfileAndTheFollows() throws Exception {
        Users ana = createUser("Ana");
        Users luca = createUser("Luca");
        Users admin = createAdmin();
        mockMvc.perform(post("/rest/users/" + luca.getId() + "/follow").with(as(ana))).andExpect(status().isNoContent());
        ban(admin, luca).andExpect(status().isNoContent());
        mockMvc.perform(get("/rest/users/me/following").with(as(ana))).andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(patch("/rest/admin/users/" + luca.getId() + "/unban").with(as(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/rest/users/" + luca.getId()).with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relation").value("FOLLOWING"));
        mockMvc.perform(get("/rest/users/me/following").with(as(ana))).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/rest/admin/users/banned").with(as(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    private Users createAdmin() {
        Users admin = createUser("Admin");
        admin.setRole(UserRole.ADMIN);
        return save(admin);
    }

    private void report(Users reporter, Users reported, String body) throws Exception {
        mockMvc.perform(post("/rest/users/" + reported.getId() + "/report").with(as(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    private String firstOpenReportId(Users admin) throws Exception {
        String json = mockMvc.perform(get("/rest/admin/reports").param("status", "OPEN").with(as(admin)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.content[0].id");
    }

    private ResultActions review(Users admin, String reportId, String status) throws Exception {
        return mockMvc.perform(patch("/rest/admin/reports/" + reportId).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"" + status + "\" }"));
    }

    private ResultActions ban(Users admin, Users user) throws Exception {
        return mockMvc.perform(patch("/rest/admin/users/" + user.getId() + "/ban").with(as(admin)));
    }
}
