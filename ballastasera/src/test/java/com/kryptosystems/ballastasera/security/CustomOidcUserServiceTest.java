package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.enums.AvatarSource;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El login guarda siempre la foto de Google, pero solo la muestra si el usuario eligio la de Google. */
class CustomOidcUserServiceTest {

    private static final String GOOGLE_PHOTO = "https://lh3.googleusercontent.com/new";
    private static final String CUSTOM_PHOTO = "http://localhost/logos/avatars/ana/avatar-1.webp";

    @Test
    void loginUpdatesTheShownPhotoWhenTheUserUsesGoogle() {
        Users user = new Users();

        CustomOidcUserService.applyGoogleAvatar(user, GOOGLE_PHOTO);

        assertThat(user.getGoogleAvatarUrl()).isEqualTo(GOOGLE_PHOTO);
        assertThat(user.getAvatarUrl()).isEqualTo(GOOGLE_PHOTO);
    }

    /** El problema de antes: la foto subida se pisaba en cada login. */
    @Test
    void loginKeepsAnUploadedPhoto() {
        Users user = new Users();
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarUrl(CUSTOM_PHOTO);

        CustomOidcUserService.applyGoogleAvatar(user, GOOGLE_PHOTO);

        assertThat(user.getAvatarUrl()).isEqualTo(CUSTOM_PHOTO);
        assertThat(user.getGoogleAvatarUrl()).isEqualTo(GOOGLE_PHOTO);
    }

    @Test
    void loginKeepsNoPhotoWhenTheUserChoseNone() {
        Users user = new Users();
        user.setAvatarSource(AvatarSource.NONE);

        CustomOidcUserService.applyGoogleAvatar(user, GOOGLE_PHOTO);

        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getGoogleAvatarUrl()).isEqualTo(GOOGLE_PHOTO);
    }
}
