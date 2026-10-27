package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.services.manager.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda los emails de organizador solo después del commit: si la transacción hace rollback
 * (p. ej. 409 CONCURRENT_UPDATE), Spring descarta el evento y no sale nada. Sin transacción activa
 * el evento también se descarta: quien lo publica tiene que ser @Transactional.
 * EmailService es @Async: ni el commit ni la respuesta HTTP esperan al envío.
 */
@Component
@RequiredArgsConstructor
public class OrganizerEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizerClaimed(OrganizerClaimedEvent event) {
        emailService.sendOrganizerClaimedEmail(event.getToEmail(), event.getOrganizerName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizerRequestReviewed(OrganizerRequestReviewedEvent event) {
        if (event.getStatus() == OrganizerRequestStatus.APPROVED) {
            emailService.sendOrganizerApprovedEmail(event.getToEmail(), event.getOrganizerName());
        } else {
            emailService.sendOrganizerRejectedEmail(event.getToEmail(), event.getOrganizerName(),
                    event.getRejectionReason(), event.getRejectionMessage());
        }
    }
}