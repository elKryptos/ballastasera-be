package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VenueUpdateDto {
    @Positive
    private Long cityId;

    @Size(max = 255)
    @Pattern(regexp = "(?s).*\\S.*")
    private String name;

    @Size(max = 255)
    @Pattern(regexp = "(?s).*\\S.*")
    private String address;

    @Size(max = 255)
    private String postalCode;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Size(max = 255)
    private String description;
}
