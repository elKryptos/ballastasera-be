package com.kryptosystems.ballastasera.events;

import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Borra del bucket las fotos de perfil que ya no se usan, solo despues del commit. Igual que
 * OrganizerLogoListener: quien publica el evento tiene que ser @Transactional. Si S3 falla solo queda un
 * archivo huerfano: se registra y no se propaga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAvatarListener {

    private final ObjectStorageService objectStorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserAvatarDeleted(UserAvatarDeletedEvent event) {
        try {
            objectStorageService.deleteUserAvatar(event.getAvatarUrl());
        } catch (MediaStorageException e) {
            log.warn("Failed to delete user avatar: {}", event.getAvatarUrl(), e);
        }
    }
}
