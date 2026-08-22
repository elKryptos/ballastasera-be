package com.kryptosystems.ballastasera.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private String publicBaseUrl;
}
