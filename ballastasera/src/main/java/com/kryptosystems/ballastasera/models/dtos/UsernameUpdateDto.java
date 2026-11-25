package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.utilities.UsernameUtils;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * PATCH /users/me/username. Aparte de PATCH /auth/me, que reemplaza el perfil entero: un FE que no mandara el campo
 * lo borraria. null o "" lo quita. Llega ya en minusculas: el FE lo normaliza mientras se escribe.
 */
@Getter
@Setter
public class UsernameUpdateDto {

    @Pattern(regexp = "^$|" + UsernameUtils.REGEX, message = "invalid username")
    private String username;
}
