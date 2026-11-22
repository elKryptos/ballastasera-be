package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import com.kryptosystems.ballastasera.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Foto de perfil. S3 y cwebp son mocks (en el test no hay RustFS ni el binario); el resto es real.
 * Los mocks dan otro contexto de Spring que el de los demas tests de integracion: arranca una vez mas.
 */
class AvatarIntegrationTest extends IntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final String GOOGLE_PHOTO = "https://lh3.googleusercontent.com/ana";
    private static final String CUSTOM_PHOTO = "http://localhost/logos/avatars/ana/avatar-1.webp";

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @MockitoBean
    private WebpConverterService webpConverterService;

    @Test
    void anUploadedPhotoBecomesTheProfilePhoto() throws Exception {
        Users ana = userWithGooglePhoto();
        mockStorage();

        upload(ana, JPEG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(CUSTOM_PHOTO))
                .andExpect(jsonPath("$.avatarSource").value("CUSTOM"))
                .andExpect(jsonPath("$.googleAvatarUrl").value(GOOGLE_PHOTO));
    }

    /** Volver a la de Google o quedarse sin foto borra del bucket la subida (despues del commit). */
    @Test
    void switchingBackToGoogleOrNoneDeletesTheUploadedPhoto() throws Exception {
        Users ana = userWithGooglePhoto();
        mockStorage();
        upload(ana, JPEG).andExpect(status().isOk());

        changeSource(ana, "GOOGLE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(GOOGLE_PHOTO))
                .andExpect(jsonPath("$.avatarSource").value("GOOGLE"));
        verify(objectStorageService, timeout(1000)).deleteUserAvatar(CUSTOM_PHOTO);

        changeSource(ana, "NONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.avatarSource").value("NONE"));
    }

    @Test
    void aPhotoOverFiveMegabytesIsRejected() throws Exception {
        Users ana = userWithGooglePhoto();

        upload(ana, new byte[5 * 1024 * 1024 + 1]).andExpect(status().isContentTooLarge());

        verify(objectStorageService, never()).uploadUserAvatar(any(), any());
    }

    @Test
    void aFileThatIsNotAnImageIsRejected() throws Exception {
        Users ana = userWithGooglePhoto();

        upload(ana, "no soy una foto".getBytes()).andExpect(status().isUnsupportedMediaType());
    }

    /** CUSTOM no se elige con JSON: una foto propia se sube con el multipart. */
    @Test
    void customCannotBeChosenWithoutUploading() throws Exception {
        Users ana = userWithGooglePhoto();

        changeSource(ana, "CUSTOM").andExpect(status().isBadRequest());
    }

    private Users userWithGooglePhoto() {
        Users ana = createUser("Ana");
        ana.setGoogleAvatarUrl(GOOGLE_PHOTO);
        ana.setAvatarUrl(GOOGLE_PHOTO);
        return save(ana);
    }

    private void mockStorage() {
        when(webpConverterService.convertToWebp(any(), anyInt())).thenReturn(new byte[]{1});
        when(objectStorageService.uploadUserAvatar(any(), any())).thenReturn(CUSTOM_PHOTO);
    }

    private ResultActions upload(Users user, byte[] content) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PATCH, "/rest/users/me/avatar")
                .file(new MockMultipartFile("avatar", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, content))
                .with(as(user)));
    }

    private ResultActions changeSource(Users user, String source) throws Exception {
        return mockMvc.perform(patch("/rest/users/me/avatar/source").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"source\": \"%s\" }".formatted(source)));
    }
}
