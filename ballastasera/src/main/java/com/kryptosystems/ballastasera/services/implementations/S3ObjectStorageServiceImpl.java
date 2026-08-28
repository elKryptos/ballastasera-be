package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.utilities.ImageTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    private final S3Client s3Client;

    @Value("${storage.raw-bucket}")
    private String rawBucket;

    @Value("${storage.bucket}")
    private String finalBucket;

    @Value("${storage.public-base-url}")
    private String publicBaseUrl;

    @Override
    public void uploadEventFlyerRaw(UUID eventId, byte[] content) {
        String extension = ImageTypeValidator.detectExtension(content);
        putObject(rawBucket, "events/" + eventId, content, CONTENT_TYPES.get(extension));
    }

    @Override
    public String uploadEventFlyerFinal(UUID eventId, byte[] webpContent) {
        String key = "events/" + eventId;
        putObject(finalBucket, key, webpContent, "image/webp");
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void deleteEventFlyerRaw(UUID eventId) {
        delete(rawBucket, "events/" + eventId);
    }

    @Override
    public void deleteEventFlyerFinal(UUID eventId) {
        delete(finalBucket, "events/" + eventId);
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
