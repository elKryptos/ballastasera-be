package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Perfil publico de GET /users/{id}. Solo la edad, nunca la fecha de nacimiento.
 * whatsapp es null si el dueño no lo hizo visible para quien mira (UserMapper.toProfileDto).
 * relation y los contadores dependen de quien mira: no pasan por ninguna cache.
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
    private SocialRelation relation;
    private long followersCount;
    private long followingCount;
    private long eventsCount;
}
