package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
class S3ObjectStorageServiceImplTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final String RAW_BUCKET = "ballastasera-media-raw";
    private static final String FINAL_BUCKET = "ballastasera-media";
    private static final String PUBLIC_BASE_URL = "http://localhost:8081/media";

    @Mock
    private S3Client s3Client;

    private S3ObjectStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new S3ObjectStorageServiceImpl(s3Client);
        ReflectionTestUtils.setField(service, "rawBucket", RAW_BUCKET);
        ReflectionTestUtils.setField(service, "finalBucket", FINAL_BUCKET);
        ReflectionTestUtils.setField(service, "publicBaseUrl", PUBLIC_BASE_URL);
    }

    @Test
    void uploadEventFlyerRawStoresJpegInRawBucketUnderFixedKey() {
        UUID eventId = UUID.randomUUID();

        service.uploadEventFlyerRaw(eventId, JPEG_BYTES);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(RAW_BUCKET);
        assertThat(request.key()).isEqualTo("events/" + eventId);
        assertThat(request.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void uploadEventFlyerRawRejectsContentThatIsNotAnImage() {
        assertThatThrownBy(() -> service.uploadEventFlyerRaw(UUID.randomUUID(), "GIF89a".getBytes()))
                .isInstanceOf(InvalidMediaTypeException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadEventFlyerRawWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> service.uploadEventFlyerRaw(UUID.randomUUID(), JPEG_BYTES))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void uploadEventFlyerFinalStoresWebpInFinalBucketAndReturnsPublicUrl() {
        UUID eventId = UUID.randomUUID();
        byte[] webpBytes = {1, 2, 3};

        String url = service.uploadEventFlyerFinal(eventId, webpBytes);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(FINAL_BUCKET);
        assertThat(request.key()).isEqualTo("events/" + eventId);
        assertThat(request.contentType()).isEqualTo("image/webp");
        assertThat(url).isEqualTo(PUBLIC_BASE_URL + "/events/" + eventId);
    }

    @Test
    void uploadEventFlyerFinalWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> service.uploadEventFlyerFinal(UUID.randomUUID(), new byte[]{1, 2, 3}))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void deleteEventFlyerRawRemovesObjectFromRawBucket() {
        UUID eventId = UUID.randomUUID();

        service.deleteEventFlyerRaw(eventId);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(RAW_BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("events/" + eventId);
    }

    @Test
    void deleteEventFlyerRawWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.deleteEventFlyerRaw(UUID.randomUUID()))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void deleteEventFlyerFinalRemovesObjectFromFinalBucket() {
        UUID eventId = UUID.randomUUID();

        service.deleteEventFlyerFinal(eventId);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(FINAL_BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("events/" + eventId);
    }

    @Test
    void deleteEventFlyerFinalWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.deleteEventFlyerFinal(UUID.randomUUID()))
                .isInstanceOf(MediaStorageException.class);
    }
}
