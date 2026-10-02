package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.VenueType;
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
    private VenueType type;
    private String address;
    private Double latitude;
    private Double longitude;
    private String description;
    private String website;
    private String whatsapp;
    private String email;
    private String facebook;
    private String instagram;
    private String youtube;
    private String tiktok;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
