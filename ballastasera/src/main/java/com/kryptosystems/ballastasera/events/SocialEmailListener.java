package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.services.manager.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails de la campanita («ti segue», «ora siete amici» e invitaciones), solo despues del commit, como
 * OrganizerEmailListener. Quien publica ya comprobo que la persona quiere emails (email_notifications): aca solo se
 * envia.
 */
@Component
@RequiredArgsConstructor
public class SocialEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewFollower(NewFollowerEvent event) {
        emailService.sendNewFollowerEmail(event.getToEmail(), event.getFollowerId(), event.getFollowerName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendshipCreated(FriendshipCreatedEvent event) {
        emailService.sendNewFriendEmail(event.getToEmail(), event.getFriendId(), event.getFriendName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteSent(InviteSentEvent event) {
        emailService.sendInviteEmail(event.getToEmail(), event.getInviterName(), event.getEventId(),
                event.getEventTitle(), event.getEventStartAt(), event.getPhrase());
    }
}
