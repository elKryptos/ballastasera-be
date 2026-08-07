package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {

    /** Handle de Instagram, con o sin @ inicial (se normaliza en el service). */
    @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "Instagram handle invalido")
    private String instagram;

    @NotNull
    private Boolean showProfilePublic;
}