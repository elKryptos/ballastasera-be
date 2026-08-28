package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.services.manager.EventFlyerProcessingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        updateStatus(eventId, FlyerStatus.READY, finalUrl);
        log.info("Flyer for event {} is ready", eventId);
        objectStorageService.deleteEventFlyerRaw(eventId);
    }

    private void updateStatus(UUID eventId, FlyerStatus status, String flyerUrl) {
        eventsRepository.findById(eventId).ifPresent(event -> {
            event.setFlyerStatus(status);
            if (flyerUrl != null) {
                event.setFlyerUrl(flyerUrl);
            }
            eventsRepository.save(event);
        });
    }
}
