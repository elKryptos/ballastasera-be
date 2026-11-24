package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.repositories.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Los avisos de la campanita se borran a los 60 dias (social-plan.md §3), cada noche, como RevokedTokensCleanupJob. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationsCleanupJob {

    static final Duration RETENTION = Duration.ofDays(60);

    private final NotificationsRepository notificationsRepository;

    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Rome")
    @Transactional
    public void deleteOldNotifications() {
        int deleted = notificationsRepository.deleteOlderThan(OffsetDateTime.now().minus(RETENTION));
        log.info("Avisos borrados por tener mas de {} dias: {}", RETENTION.toDays(), deleted);
    }
}
