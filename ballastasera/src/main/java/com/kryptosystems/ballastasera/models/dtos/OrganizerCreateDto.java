package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.OrganizerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizerCreateDto {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    private OrganizerType type;

    @Size(max = 2000)
    private String description;

    private String logoUrl;
    private String website;
    private String phone;
    private String contactEmail;
    private String instagram;
    private String facebook;
}
