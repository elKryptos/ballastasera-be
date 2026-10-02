package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.VenueType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

@Getter
@Setter
public class VenueCreateDto {
    /** Opcional: solo si el lugar tiene perfil propio de organizer. */
    private UUID organizerId;

    @NotNull
    @Positive
    private Long cityId;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private VenueType type;

    @NotBlank
    @Size(max = 150)
    private String address;

    /** Opcional: si lat o lng faltan, el servicio geocodifica la dirección. */
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Size(max = 500)
    private String description;

    /** Contactos opcionales: null o "" = sin valor (el mapper guarda "" como null). En el PATCH, "" borra el valor actual. */
    @URL
    @Size(max = 100)
    private String website;

    @Pattern(regexp = "^$|^\\+?[0-9]{6,15}$", message = "whatsapp must be a phone number with country code")
    private String whatsapp;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 100)
    @Pattern(regexp = "^$|^https://(www\\.|m\\.)?facebook\\.com/.+", message = "must be a facebook.com URL")
    private String facebook;

    @Size(max = 100)
    @Pattern(regexp = "^$|^https://(www\\.)?instagram\\.com/.+", message = "must be an instagram.com URL")
    private String instagram;

    @Size(max = 100)
    @Pattern(regexp = "^$|^https://(www\\.)?youtube\\.com/.+", message = "must be a youtube.com URL")
    private String youtube;

    @Size(max = 100)
    @Pattern(regexp = "^$|^https://(www\\.)?tiktok\\.com/@.+", message = "must be a tiktok.com URL")
    private String tiktok;
}
