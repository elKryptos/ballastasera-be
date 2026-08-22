package com.kryptosystems.ballastasera.services.manager;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ObjectStorageService {

    String uploadEventFlyer(UUID eventId, MultipartFile file);

    void delete(String key);
}
