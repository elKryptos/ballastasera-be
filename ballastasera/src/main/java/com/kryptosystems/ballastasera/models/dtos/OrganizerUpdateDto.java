package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizerUpdateDto {

    @NotBlank
    private String name;

    @Size(max = 2000)
    private String description;

    private String logoUrl;
    private String website;
    private String phone;
    private String contactEmail;
    private String instagram;
    private String facebook;
}
