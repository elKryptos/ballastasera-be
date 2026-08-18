package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VenueCreateDto {

    @NotNull
    private UUID organizerId;

    @NotNull
    private Long cityId;

    @NotBlank
    private String name;

    @NotBlank
    private String address;

    private String postalCode;
    private String description;

    private Double latitude;
    private Double longitude;
}
