package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Ciudad donde vive el usuario, tal como la devuelve Photon (layer=city) en el FE: ya normalizada, para que
 * «torino» y «Turín» cuenten como la misma. Entrada en PATCH /auth/me y salida en /auth/me.
 */
@Getter
@Setter
public class HomeCityDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String region;

    @NotNull
    @Pattern(regexp = "^[A-Z]{2}$", message = "country must be an ISO 3166-1 alpha-2 code, e.g. IT")
    private String country;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double latitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double longitude;
}
