package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Borra del bucket los logos de organizador que ya no se usan, solo después del commit. Igual que
 * OrganizerEmailListener: sin transacción activa el evento se descarta, quien lo publica tiene que ser
 * @Transactional. Si S3 falla solo queda un archivo huérfano: se registra y no se propaga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizerLogoListener {

    private final ObjectStorageService objectStorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizerLogoDeleted(OrganizerLogoDeletedEvent event) {
        try {
            objectStorageService.deleteOrganizerLogo(event.getLogoUrl());
        } catch (MediaStorageException e) {
            log.warn("Failed to delete organizer logo: {}", event.getLogoUrl(), e);
        }
    }
}
