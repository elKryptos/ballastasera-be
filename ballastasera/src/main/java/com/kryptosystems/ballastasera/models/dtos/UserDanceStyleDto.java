package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.DanceLevel;
import lombok.Getter;
import lombok.Setter;

/** Estilo del usuario con su nivel, en /auth/me y en el perfil publico. */
@Getter
@Setter
public class UserDanceStyleDto {
    private Long danceStyleId;
    private String name;
    private String slug;
    private DanceLevel level;
}
