package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.VenueType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Pin de un venue en el mapa. El filtro por tipo y por area visible lo hace el FE. */
@Getter
@Setter
public class VenueMapPinDto {
    private UUID id;
    private String name;
    private VenueType type;
    private String address;
    private Double latitude;
    private Double longitude;
}
