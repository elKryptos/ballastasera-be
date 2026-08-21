package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.services.manager.EmailService;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.resend.Resend;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final Resend resend;
    private final String fromEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

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
            log.warn("Impossibile inviare l'email di approvazione a {}", toEmail, e);
        }
    }

    private String buildEmailContent(String organizerName) {
        return "<h1>Profilo verificato</h1>" +
                "<p>Il tuo profilo organizzatore <strong>" + organizerName + "</strong> è stato verificato.</p>" +
                "<p>Ora puoi pubblicare eventi da <a href=\"" + frontendUrl + "\">Ballastasera</a>.</p>" +
                "<p>A presto sulla pista!</p>";
    }

    private String buildClaimedEmail(String organizerName) {
        return "<h1>Profilo assegnato</h1>" +
                "<p>Il profilo organizzatore <strong>" + organizerName + "</strong> ti è stato assegnato.</p>" +
                "<p>Ora puoi pubblicare eventi da <a href=\"" + frontendUrl + "\">Ballastasera</a>.</p>" +
                "<p>A presto sulla pista!</p>";
    }
}
