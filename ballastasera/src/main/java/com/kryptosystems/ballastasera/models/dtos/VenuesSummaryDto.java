package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.VenueType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VenuesSummaryDto {
    private UUID id;
    private String name;
    private VenueType type;
    private String address;
    private String cityName;
    private Double latitude;
    private Double longitude;
}
