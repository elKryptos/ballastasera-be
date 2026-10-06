package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.utilities.ImageTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageServiceImpl implements ObjectStorageService {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private static final String VENUE_LOGO_PREFIX = "venues/";

    private final S3Client s3Client;

    @Value("${storage.raw-bucket}")
    private String rawBucket;

    @Value("${storage.flyers-bucket}")
    private String flyersBucket;

    @Value("${storage.flyers-public-base-url}")
    private String flyersPublicBaseUrl;

    @Value("${storage.logos-bucket}")
    private String logosBucket;

    @Value("${storage.logos-public-base-url}")
    private String logosPublicBaseUrl;

    @Override
    public void uploadEventFlyerRaw(UUID eventId, byte[] content) {
        String extension = ImageTypeValidator.detectExtension(content);
        putObject(rawBucket, eventId.toString(), content, CONTENT_TYPES.get(extension));
    }

    @Override
    public String uploadEventFlyerFinal(UUID eventId, byte[] webpContent) {
        putObject(flyersBucket, eventId.toString(), webpContent, "image/webp");
        return UriComponentsBuilder.fromUriString(flyersPublicBaseUrl)
                .path("/{id}")
                .buildAndExpand(eventId)
                .toUriString();
    }

    @Override
    public void deleteEventFlyerRaw(UUID eventId) {
        delete(rawBucket, eventId.toString());
    }

    @Override
    public void deleteEventFlyerFinal(UUID eventId) {
        delete(flyersBucket, eventId.toString());
    }

    @Override
    public String uploadVenueLogo(UUID venueId, byte[] webpContent) {
        // Clave nueva en cada subida: nginx sirve /logos como "immutable" (1 año),
        // si se pisara la misma clave el navegador seguiria mostrando el logo viejo.
        String key = VENUE_LOGO_PREFIX + venueId + "/logo-" + UUID.randomUUID() + ".webp";
        putObject(logosBucket, key, webpContent, "image/webp");
        return logosPublicBaseUrl + "/" + key;
    }

    @Override
    public void deleteVenueLogo(String logoUrl) {
        String base = logosPublicBaseUrl + "/";
        // Solo borra URLs generadas por uploadVenueLogo: una URL externa se ignora.
        if (logoUrl != null && logoUrl.startsWith(base)) {
            delete(logosBucket, logoUrl.substring(base.length()));
        }
    }

    private void putObject(String targetBucket, String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new MediaStorageException("Failed to store flyer", e);
        }
    }

    private void delete(String targetBucket, String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            throw new MediaStorageException("Failed to delete stored flyer", e);
        }
    }
}
