package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.StorageProperties;
import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageServiceTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Mock
    private S3Client s3Client;

    private StorageProperties properties;
    private S3ObjectStorageService service;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setBucket("ballastasera-media");
        properties.setPublicBaseUrl("http://localhost:8081/media");
        service = new S3ObjectStorageService(s3Client, properties);
    }

    @Test
    void uploadEventFlyerStoresJpegInEventFolderAndReturnsPublicUrl() {
        UUID eventId = UUID.randomUUID();
        MockMultipartFile file =
                new MockMultipartFile("file", "flyer.jpg", "image/jpeg", JPEG_BYTES);

        String url = service.uploadEventFlyer(eventId, file);

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("ballastasera-media");
        assertThat(request.key()).startsWith("events/" + eventId + "/").endsWith(".jpg");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(url).isEqualTo(properties.getPublicBaseUrl() + "/" + request.key());
    }

    @Test
    void uploadEventFlyerRejectsContentThatIsNotAnImage() {
        MockMultipartFile file =
                new MockMultipartFile("file", "flyer.gif", "image/gif", "GIF89a".getBytes());

        assertThatThrownBy(() -> service.uploadEventFlyer(UUID.randomUUID(), file))
                .isInstanceOf(InvalidMediaTypeException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadEventFlyerWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> service.uploadEventFlyer(
                UUID.randomUUID(),
                new MockMultipartFile("file", "f.jpg", "image/jpeg", JPEG_BYTES)))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void deleteRemovesObjectFromConfiguredBucket() {
        service.delete("events/x/1.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("ballastasera-media");
        assertThat(captor.getValue().key()).isEqualTo("events/x/1.jpg");
    }

    @Test
    void deleteWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.delete("events/x/1.jpg"))
                .isInstanceOf(MediaStorageException.class);
    }
}
