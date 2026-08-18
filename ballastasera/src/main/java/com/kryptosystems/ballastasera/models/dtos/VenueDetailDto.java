package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class VenueDetailDto {
    private UUID id;
    private String name;
    private String address;
    private String postalCode;
    private String description;
    private Long cityId;
    private String cityName;
    private Double latitude;
    private Double longitude;
    private UUID organizerId;
    private String organizerName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
