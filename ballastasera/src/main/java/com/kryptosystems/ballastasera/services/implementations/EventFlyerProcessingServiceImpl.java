package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.services.manager.EventFlyerProcessingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventFlyerProcessingServiceImpl implements EventFlyerProcessingService {

    private final WebpConverterService webpConverterService;
    private final ObjectStorageService objectStorageService;
    private final EventsRepository eventsRepository;

    @Async
    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#eventId")
    public void convertAndPublish(UUID eventId, byte[] rawContent) {
        byte[] webpContent;
        try {
            webpContent = webpConverterService.convertToWebp(rawContent);
        } catch (MediaStorageException e) {
            log.error("Failed to convert flyer to webp for event {}", eventId, e);
            updateStatus(eventId, FlyerStatus.FAILED, null);
            return;
        }
        String finalUrl = objectStorageService.uploadEventFlyerFinal(eventId, webpContent);
        // Evento borrado (vacío) o flyer quitado mientras se convertía (NONE): no dejamos el WebP
        // publicado. Con otro estado llegó un flyer más nuevo a la misma clave: no se borra nada,
        // o se borraría el suyo.
        FlyerStatus before = updateStatus(eventId, FlyerStatus.READY, finalUrl).orElse(FlyerStatus.NONE);
        if (before == FlyerStatus.NONE) {
            objectStorageService.deleteEventFlyerFinal(eventId);
            log.info("Flyer for event {} was removed while converting: WebP discarded", eventId);
        } else if (before == FlyerStatus.PROCESSING) {
            log.info("Flyer for event {} is ready", eventId);
        }
        objectStorageService.deleteEventFlyerRaw(eventId);
    }

    /** Guarda el resultado de la conversión solo si el evento todavía la esperaba (PROCESSING).
     * Devuelve el estado que tenía el evento, o vacío si ya no existe. */
    private Optional<FlyerStatus> updateStatus(UUID eventId, FlyerStatus status, String flyerUrl) {
        return eventsRepository.findById(eventId).map(event -> {
            FlyerStatus current = event.getFlyerStatus();
            if (current == FlyerStatus.PROCESSING) {
                event.setFlyerStatus(status);
                if (flyerUrl != null) {
                    event.setFlyerUrl(flyerUrl);
                }
                eventsRepository.save(event);
            }
            return current;
        });
    }
}
