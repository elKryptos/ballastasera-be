package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;
import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Se publica al aprobar una NEW o al rechazar una solicitud (también el rechazo automático de las CLAIM
 * que compiten por la misma página). Una CLAIM aprobada no lo publica: su email lo manda claim() con
 * OrganizerClaimedEvent. Se crea con approved() o rejected().
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OrganizerRequestReviewedEvent {
    private final String toEmail;
    private final String organizerName;
    private final OrganizerRequestStatus status;
    /** Solo en REJECTED. */
    private final OrganizerRejectReason rejectionReason;
    /** Solo en REJECTED, y puede ser null: el mensaje del admin es opcional salvo con OTHER. */
    private final String rejectionMessage;

    public static OrganizerRequestReviewedEvent approved(String toEmail, String organizerName) {
        return new OrganizerRequestReviewedEvent(toEmail, organizerName, OrganizerRequestStatus.APPROVED, null, null);
    }

    public static OrganizerRequestReviewedEvent rejected(String toEmail, String organizerName,
                                                         OrganizerRejectReason reason, String message) {
        return new OrganizerRequestReviewedEvent(toEmail, organizerName, OrganizerRequestStatus.REJECTED, reason, message);
    }
}