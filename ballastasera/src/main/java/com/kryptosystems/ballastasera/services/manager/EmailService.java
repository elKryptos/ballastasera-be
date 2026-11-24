package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.InvitePhrase;
import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface EmailService {

    void sendOrganizerApprovedEmail(String toEmail, String organizerName);
    void sendOrganizerClaimedEmail(String toEmail, String organizerName);
    /** message es opcional: si es null, el email lleva solo el motivo. */
    void sendOrganizerRejectedEmail(String toEmail, String organizerName, OrganizerRejectReason reason, String message);
    void sendOrganizerUnclaimedEmail(String toEmail, String organizerName);
    void sendOrganizerDeletedEmail(String toEmail, String organizerName);

    /** «Ora siete amici»: el boton lleva al perfil del nuevo amigo. */
    void sendNewFriendEmail(String toEmail, UUID friendId, String friendName);
    /** «Invita a ballare»: la frase elegida y el boton a la serata. */
    void sendInviteEmail(String toEmail, String inviterName, UUID eventId, String eventTitle,
                         OffsetDateTime eventStartAt, InvitePhrase phrase);
}
