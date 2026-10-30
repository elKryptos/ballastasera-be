package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;

public interface EmailService {

    void sendOrganizerApprovedEmail(String toEmail, String organizerName);
    void sendOrganizerClaimedEmail(String toEmail, String organizerName);
    /** message es opcional: si es null, el email lleva solo el motivo. */
    void sendOrganizerRejectedEmail(String toEmail, String organizerName, OrganizerRejectReason reason, String message);
    void sendOrganizerUnclaimedEmail(String toEmail, String organizerName);
}
