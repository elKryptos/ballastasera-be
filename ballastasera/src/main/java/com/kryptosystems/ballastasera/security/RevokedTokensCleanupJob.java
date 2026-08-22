package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.repositories.RevokedTokensRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RevokedTokensCleanupJob {

    private final RevokedTokensRepository revokedTokensRepository;

    /** Un token revocado ya no hace falta guardarlo despues de que igualmente hubiera expirado. */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteExpiredRevokedTokens() {
        revokedTokensRepository.deleteAllExpiredBefore(OffsetDateTime.now());
    }
}