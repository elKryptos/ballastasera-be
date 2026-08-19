package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class VenueDetailDto {
    private UUID id;
    private UUID organizerId;
    private String organizerName;
    private Long cityId;
    private String cityName;
    private String name;
    private String address;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
