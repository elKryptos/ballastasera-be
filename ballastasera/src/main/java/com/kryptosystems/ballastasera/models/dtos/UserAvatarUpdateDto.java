package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.AvatarSource;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** PATCH /users/me/avatar/source: «Usa la foto di Google» (GOOGLE) o «Nessuna foto» (NONE).
 * Una propia se sube con PATCH /users/me/avatar (multipart). */
@Getter
@Setter
public class UserAvatarUpdateDto {

    @NotNull
    private AvatarSource source;

    @AssertTrue(message = "source must be GOOGLE or NONE: upload a photo with PATCH /users/me/avatar")
    public boolean isSourceValid() {
        return source != AvatarSource.CUSTOM;
    }
}
