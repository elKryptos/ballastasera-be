package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.DanceLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Un estilo elegido en el formulario de perfil (dentro de UserUpdateDto.danceStyles). */
@Getter
@Setter
public class UserDanceStyleUpdateDto {

    @NotNull
    private Long danceStyleId;

    @NotNull
    private DanceLevel level;
}
