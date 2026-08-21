package com.kryptosystems.ballastasera.services.manager;

public interface EmailService {

    void sendOrganizerApprovedEmail(String toEmail, String organizerName);
    void sendOrganizerClaimedEmail(String toEmail, String organizerName);
}
