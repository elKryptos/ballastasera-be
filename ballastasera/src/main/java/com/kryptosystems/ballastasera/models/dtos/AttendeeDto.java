package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Solo aparece aqui quien tiene el perfil publico (Users.showProfilePublic) y no esta
 * borrado ni baneado. Al tocarlo, el front abre GET /users/{id} (UserProfileDto).
 * El conteo total de "van" no depende de esto, se calcula aparte sobre event_attendance.
 */
@Getter
@Setter
public class AttendeeDto {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String instagram;
}