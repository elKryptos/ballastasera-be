package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizerUpdateDto {

    @Size(max = 120)
    @Pattern(regexp = "(?s).*\\S.*", message = "name must not be blank")
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
