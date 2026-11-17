package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Perfil publico de GET /users/{id}. Solo la edad, nunca la fecha de nacimiento.
 * whatsapp es null si el dueño no lo hizo visible (UserMapper.toProfileDto).
 */
@Getter
@Setter
public class UserProfileDto {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String instagram;
    private Integer age;
    private Gender gender;
    private DanceRole danceRole;
    private String whatsapp;
    private List<UserDanceStyleDto> danceStyles;
}
