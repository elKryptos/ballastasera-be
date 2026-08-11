package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Solo aparece aqui quien activo "mostrar mi Instagram" en su perfil
 * (Users.showProfilePublic). El conteo total de "van" no depende de esto,
 * se calcula aparte sobre event_attendance sin filtrar por opt-in.
 */
@Getter
@Setter
public class AttendeeDto {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String instagram;
}