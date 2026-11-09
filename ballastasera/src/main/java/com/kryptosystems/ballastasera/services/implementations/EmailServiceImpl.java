package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;
import com.kryptosystems.ballastasera.services.manager.EmailService;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.resend.Resend;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Los textos que no escribe el backend (nombre del organizador, mensaje del admin) pasan por
 * HtmlUtils.htmlEscape antes de entrar en el HTML: el cliente de correo los muestra como texto
 * y no los interpreta como etiquetas.
 *
 * Todos los correos comparten el mismo layout (cabecera con logo, cuerpo, botón y pie): cada uno
 * solo aporta título, texto y etiqueta del botón. HTML de correo: tablas y estilos inline, sin
 * gradientes imprescindibles ni SVG (Gmail/Outlook no los muestran).
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    // Colores de styles.css del FE: cabecera con el tema oscuro, cuerpo con el tema claro.
    private static final String INK = "#1a0e2e";          // --color-ink
    private static final String ROSE = "#ff4d6d";         // --color-rose
    private static final String AMBER = "#ffa24c";        // --color-amber
    private static final String VIOLET = "#8b5cf6";       // --color-violet
    private static final String BONE = "#fdf2ec";         // --color-bone (oscuro)
    private static final String PAPER = "#fbf8f3";        // --color-ground (claro)
    private static final String TEXT = "#1d2640";         // --color-bone (claro)
    private static final String MUTED = "#5f6d8c";        // --color-muted (claro)
    private static final String LINK = "#be123c";         // --ev-link (claro)
    private static final String LINE = "#e4e6ec";         // --color-line (claro) sin transparencia, por Outlook
    private static final String FONT_DISPLAY = "'Bricolage Grotesque','Arial Black',Arial,sans-serif";
    private static final String FONT_BODY = "'Instrument Sans',Helvetica,Arial,sans-serif";

    private final Resend resend;
    private final String fromEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${email.logo-url}")
    private String logoUrl;

    public EmailServiceImpl(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail
    ) {
        this.resend = new Resend(apiKey);
        this.fromEmail = fromEmail;
    }

    @Override
    @Async
    public void sendOrganizerApprovedEmail(String toEmail, String organizerName) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("Il tuo profilo organizzatore è stato approvato.")
                .html(buildEmailContent(organizerName))
                .build();
        try {
            CreateEmailResponse data = resend.emails().send(params);
        } catch (ResendException e) {
            log.warn("Impossibile inviare l'email di approvazione a {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendOrganizerClaimedEmail(String toEmail, String organizerName) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("Il profilo organizzatore ti è stato assegnato.")
                .html(buildClaimedEmail(organizerName))
                .build();
        try {
            CreateEmailResponse data = resend.emails().send(params);
        } catch (ResendException e) {
            log.warn("Impossibile inviare l'email di assegnazione a {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendOrganizerRejectedEmail(String toEmail, String organizerName, OrganizerRejectReason reason, String message) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("La tua richiesta organizzatore non è stata approvata.")
                .html(buildRejectedEmail(organizerName, reason, message))
                .build();
        try {
            CreateEmailResponse data = resend.emails().send(params);
        } catch (ResendException e) {
            log.warn("Impossibile inviare l'email di rifiuto a {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendOrganizerUnclaimedEmail(String toEmail, String organizerName) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("Il profilo organizzatore non è più associato al tuo account.")
                .html(buildUnclaimedEmail(organizerName))
                .build();
        try {
            CreateEmailResponse data = resend.emails().send(params);
        } catch (ResendException e) {
            log.warn("Impossibile inviare l'email di rimozione a {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendOrganizerDeletedEmail(String toEmail, String organizerName) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("Il tuo profilo organizzatore è stato eliminato.")
                .html(buildDeletedEmail(organizerName))
                .build();
        try {
            resend.emails().send(params);
        } catch (ResendException e) {
            log.warn("Impossibile inviare l'email di rimozione a {}", toEmail, e);
        }
    }

    private String buildEmailContent(String organizerName) {
        return layout(
                "Il tuo profilo organizzatore è stato verificato: ora puoi pubblicare eventi.",
                "Profilo verificato",
                paragraph("Il tuo profilo organizzatore " + strong(organizerName) + " è stato verificato.") +
                        paragraph("Ora puoi pubblicare i tuoi eventi su ballastasera."),
                "Inizia a pubblicare");
    }

    private String buildClaimedEmail(String organizerName) {
        return layout(
                "Il profilo organizzatore ti è stato assegnato: ora puoi pubblicare eventi.",
                "Profilo assegnato",
                paragraph("Il profilo organizzatore " + strong(organizerName) + " ti è stato assegnato.") +
                        paragraph("Ora puoi pubblicare i tuoi eventi su ballastasera."),
                "Inizia a pubblicare");
    }

    private String buildUnclaimedEmail(String organizerName) {
        return layout(
                "Il profilo organizzatore non è più associato al tuo account.",
                "Profilo rimosso",
                paragraph("Il profilo organizzatore " + strong(organizerName) + " non è più associato al tuo account.") +
                        paragraph("Puoi inviare una nuova richiesta da ballastasera."),
                "Vai a ballastasera");
    }

    /** Sin mensaje del admin, el recuadro lleva solo el motivo: nada de párrafo vacío. */
    private String buildRejectedEmail(String organizerName, OrganizerRejectReason reason, String message) {
        String messageBlock = message == null || message.isBlank() ? ""
                : "<p style=\"margin:8px 0 0;\">" + HtmlUtils.htmlEscape(message).replace("\n", "<br>") + "</p>";
        return layout(
                "La tua richiesta organizzatore non è stata approvata.",
                "Richiesta non approvata",
                paragraph("La tua richiesta per " + strong(organizerName) + " non è stata approvata.") +
                        callout("<p style=\"margin:0;\"><strong style=\"color:" + INK + ";\">Motivo:</strong> "
                                + rejectReasonLabel(reason) + "</p>" + messageBlock) +
                        paragraph("Puoi correggere i dati e inviare una nuova richiesta da ballastasera."),
                "Vai a ballastasera");
    }

    private String buildDeletedEmail(String organizerName) {
        return layout(
                "Il tuo profilo organizzatore è stato eliminato.",
                "Profilo eliminato",
                paragraph("Il profilo organizzatore " + strong(organizerName) + " è stato eliminato da ballastasera.") +
                        callout("<p style=\"margin:0;\">Gli eventi in programma sono stati annullati.</p>") +
                        paragraph("Se pensi che si tratti di un errore o vuoi creare un nuovo profilo, puoi inviare una richiesta da ballastasera."),
                "Vai a ballastasera");
    }

    /**
     * Esqueleto común. El preheader es el texto que el cliente de correo enseña junto al asunto en
     * la bandeja de entrada; en el cuerpo no se ve. La franja bajo la cabecera es el degradado del
     * logo; Outlook, que no pinta gradientes, la deja en rosa liso.
     */
    private String layout(String preheader, String title, String body, String ctaLabel) {
        return "<!DOCTYPE html>" +
                "<html lang=\"it\"><head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<meta name=\"color-scheme\" content=\"light\">" +
                "<meta name=\"supported-color-schemes\" content=\"light\">" +
                "<title>" + title + "</title>" +
                "</head>" +
                "<body style=\"margin:0;padding:0;background-color:" + PAPER + ";\">" +
                "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;\">" + preheader + "</div>" +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"" + PAPER + "\" style=\"background-color:" + PAPER + ";\">" +
                "<tr><td align=\"center\" style=\"padding:32px 16px;\">" +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:600px;\">" +

                // Cabecera: logo + nombre sobre el morado del FE
                "<tr><td bgcolor=\"" + INK + "\" style=\"background-color:" + INK + ";border-radius:16px 16px 0 0;padding:24px 32px;\">" +
                "<a href=\"" + frontendUrl + "\" style=\"text-decoration:none;\">" +
                "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>" +
                "<td style=\"vertical-align:middle;\">" +
                "<img src=\"" + logoUrl + "\" width=\"48\" height=\"48\" alt=\"ballastasera\" style=\"display:block;border:0;border-radius:12px;\">" +
                "</td>" +
                "<td style=\"vertical-align:middle;padding-left:14px;font-family:" + FONT_DISPLAY + ";font-size:24px;font-weight:800;letter-spacing:-0.5px;color:" + BONE + ";\">" +
                "ballastasera" +
                "</td>" +
                "</tr></table>" +
                "</a>" +
                "</td></tr>" +

                // Franja con el degradado del logo
                "<tr><td height=\"4\" bgcolor=\"" + ROSE + "\" style=\"height:4px;line-height:4px;font-size:0;background-color:" + ROSE + ";" +
                "background-image:linear-gradient(90deg," + AMBER + "," + ROSE + "," + VIOLET + ");\">&nbsp;</td></tr>" +

                // Cuerpo
                "<tr><td bgcolor=\"#ffffff\" style=\"background-color:#ffffff;padding:36px 32px 32px;font-family:" + FONT_BODY + ";font-size:16px;line-height:1.6;color:" + TEXT + ";\">" +
                "<h1 style=\"margin:0 0 20px;font-family:" + FONT_DISPLAY + ";font-size:26px;line-height:1.25;font-weight:800;color:" + INK + ";\">" + title + "</h1>" +
                body +
                button(ctaLabel) +
                "<p style=\"margin:28px 0 0;\">A presto sulla pista!</p>" +
                "</td></tr>" +

                // Pie
                "<tr><td bgcolor=\"#ffffff\" style=\"background-color:#ffffff;border-top:1px solid " + LINE + ";border-radius:0 0 16px 16px;padding:20px 32px;font-family:" + FONT_BODY + ";font-size:13px;line-height:1.5;color:" + MUTED + ";\">" +
                "<a href=\"" + frontendUrl + "\" style=\"color:" + LINK + ";font-weight:700;text-decoration:none;\">ballastasera</a>" +
                " &middot; Dove andare a ballare stasera" +
                "</td></tr>" +

                "</table>" +
                "</td></tr></table>" +
                "</body></html>";
    }

    /** Botón "a prueba de Outlook": el color va en la celda, no solo en el enlace. Texto ink sobre rosa, como en el FE. */
    private String button(String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:28px 0 0;\"><tr>" +
                "<td bgcolor=\"" + ROSE + "\" style=\"background-color:" + ROSE + ";border-radius:999px;\">" +
                "<a href=\"" + frontendUrl + "\" style=\"display:inline-block;padding:14px 28px;font-family:" + FONT_BODY + ";font-size:16px;font-weight:700;color:" + INK + ";text-decoration:none;border-radius:999px;\">" +
                label +
                "</a>" +
                "</td></tr></table>";
    }

    private String paragraph(String html) {
        return "<p style=\"margin:0 0 16px;\">" + html + "</p>";
    }

    /** Para textos que vienen del usuario (nombre del organizador): siempre escapados. */
    private String strong(String text) {
        return "<strong style=\"color:" + INK + ";\">" + HtmlUtils.htmlEscape(text) + "</strong>";
    }

    /** Recuadro destacado (motivo del rechazo, eventos anulados) con el borde rosa del FE. */
    private String callout(String html) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:4px 0 20px;\"><tr>" +
                "<td bgcolor=\"" + BONE + "\" style=\"background-color:" + BONE + ";border-left:4px solid " + ROSE + ";border-radius:8px;padding:16px 20px;color:" + TEXT + ";\">" +
                html +
                "</td></tr></table>";
    }

    /** Las mismas etiquetas que ve el admin al rechazar. */
    private String rejectReasonLabel(OrganizerRejectReason reason) {
        return switch (reason) {
            case INSTAGRAM_NOT_FOUND -> "Instagram non trovato";
            case INCOMPLETE_DATA -> "Dati incompleti";
            case NOT_DANCE_ORGANIZER -> "Non organizza serate di ballo";
            case DUPLICATE -> "Doppione";
            case OTHER -> "Altro";
        };
    }
}
