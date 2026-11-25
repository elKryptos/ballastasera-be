package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PATCH /users/me/email-notifications: toggle «Ricevi email». Aparte de PATCH /auth/me, que reemplaza el perfil
 * entero: un FE que no mandara el campo apagaria los emails sin querer. Boolean y no boolean: sin body da 400.
 */
@Getter
@Setter
public class EmailNotificationsUpdateDto {

    @NotNull
    private Boolean enabled;
}
