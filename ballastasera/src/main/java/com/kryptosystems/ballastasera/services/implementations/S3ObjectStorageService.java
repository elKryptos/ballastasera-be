package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.StorageProperties;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.utilities.ImageTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService implements ObjectStorageService {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    @Override
    public String uploadEventFlyer(UUID eventId, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new MediaStorageException("Could not read uploaded file", e);
        }
        String extension = ImageTypeValidator.detectExtension(content);
        String key = "events/" + eventId + "/" + UUID.randomUUID() + "." + extension;
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(storageProperties.getBucket())
                            .key(key)
                            .contentType(CONTENT_TYPES.get(extension))
                            .build(),
                    RequestBody.fromBytes(content));
            
        } catch (S3Exception e) {
            throw new MediaStorageException("Failed to store event flyer", e);
        }
        return storageProperties.getPublicBaseUrl() + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            throw new MediaStorageException("Failed to delete stored flyer", e);
        }
    }
}
