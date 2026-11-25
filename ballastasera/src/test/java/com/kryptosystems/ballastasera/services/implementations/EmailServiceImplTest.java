package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.InvitePhrase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las plantillas de la campanita. Los tests de integracion mockean EmailService, asi que sin esto nunca se arman: un
 * error (la fecha, un nombre sin escapar) solo apareceria en el log del hilo @Async y el email no saldria.
 */
class EmailServiceImplTest {

    private static final String FRONTEND_URL = "https://ballastasera.it";
    private static final UUID FRIEND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl("test-api-key", "noreply@ballastasera.it");
        ReflectionTestUtils.setField(service, "frontendUrl", FRONTEND_URL);
        ReflectionTestUtils.setField(service, "logoUrl", FRONTEND_URL + "/logo.png");
    }

    @Test
    void theFollowerEmailLinksToTheFollowersProfile() {
        String html = service.buildNewFollowerEmail(FRIEND_ID, "Luca");

        assertThat(html)
                .contains("Hai un nuovo follower")
                .contains("<strong style=\"color:#1a0e2e;\">Luca</strong> ha iniziato a seguirti")
                .contains("Ricambia il follow")
                .contains("href=\"" + FRONTEND_URL + "/persona/" + FRIEND_ID + "\"")
                .contains("href=\"" + FRONTEND_URL + "/il-tuo-account\"");
    }

    @Test
    void theFriendEmailLinksToTheNewFriendsProfile() {
        String html = service.buildNewFriendEmail(FRIEND_ID, "Luca");

        assertThat(html)
                .contains("Ora siete amici")
                .contains("<strong style=\"color:#1a0e2e;\">Luca</strong> ha ricambiato il tuo follow")
                .contains("href=\"" + FRONTEND_URL + "/persona/" + FRIEND_ID + "\"")
                .contains("href=\"" + FRONTEND_URL + "/il-tuo-account\"");
    }

    /** 22:00 UTC del sabado 28/11 son las 23:00 en Italia (hora de invierno). */
    @Test
    void theInviteEmailShowsThePhraseTheItalianDateAndLinksToTheSerata() {
        OffsetDateTime startAt = OffsetDateTime.of(2026, 11, 28, 22, 0, 0, 0, ZoneOffset.UTC);

        String html = service.buildInviteEmail("Ana", EVENT_ID, "Salsa al Tropical", startAt, InvitePhrase.ANDIAMO_INSIEME);

        assertThat(html)
                .contains("«Andiamo insieme?»")
                .contains("sabato 28 novembre alle 23:00")
                .contains("href=\"" + FRONTEND_URL + "/evento/" + EVENT_ID + "\"")
                .contains("href=\"" + FRONTEND_URL + "/il-tuo-account\"");
    }

    /** Nombres y titulos los escribe la gente: en el HTML van escapados, tambien en el preheader. */
    @Test
    void namesAndTitlesAreEscaped() {
        String followerHtml = service.buildNewFollowerEmail(FRIEND_ID, "<u>Ana</u>");
        String friendHtml = service.buildNewFriendEmail(FRIEND_ID, "<b>Luca</b>");
        String inviteHtml = service.buildInviteEmail("<i>Ana</i>", EVENT_ID, "Salsa & <script>", OffsetDateTime.now(),
                InvitePhrase.CI_VIENI);

        assertThat(followerHtml).doesNotContain("<u>Ana</u>").contains("&lt;u&gt;Ana&lt;/u&gt;");
        assertThat(friendHtml).doesNotContain("<b>Luca</b>").contains("&lt;b&gt;Luca&lt;/b&gt;");
        assertThat(inviteHtml)
                .doesNotContain("<i>Ana</i>")
                .doesNotContain("<script>")
                .contains("&lt;i&gt;Ana&lt;/i&gt;")
                .contains("Salsa &amp; &lt;script&gt;");
    }
}
