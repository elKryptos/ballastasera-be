package com.kryptosystems.ballastasera.models.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CityDto {

    private Long id;
    private String name;
    private String province;
    private String region;
    private String country;
    private Double latitude;
    private Double longitude;
    private String slug;

    @JsonProperty("isActive")
    private boolean active;
}
