package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.InvitePhrase;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Body de POST /users/{id}/invites: la serata y una de las frases fijas (sin texto libre). */
@Getter
@Setter
public class InviteCreateDto {

    @NotNull
    private UUID eventId;

    @NotNull
    private InvitePhrase phrase;
}
