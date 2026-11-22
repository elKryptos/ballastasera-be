package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** PATCH /users/{id}/follow: «Silenzia» (true) o «Riattiva» (false). Boolean y no boolean: sin body da 400. */
@Getter
@Setter
public class UserMuteDto {

    @NotNull
    private Boolean muted;
}
