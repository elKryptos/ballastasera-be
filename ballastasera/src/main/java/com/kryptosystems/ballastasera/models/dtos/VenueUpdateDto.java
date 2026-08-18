package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VenueUpdateDto {
    private String name;
    private String address;
    private String postalCode;
    private String description;
    private Double latitude;
    private Double longitude;
}
