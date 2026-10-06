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
    private static final String RAW_BUCKET = "ballastasera-raw-flyers";
    private static final String FLYERS_BUCKET = "ballastasera-flyers";
    private static final String FLYERS_PUBLIC_BASE_URL = "http://localhost/flyers";
    private static final String LOGOS_BUCKET = "ballastasera-logos";
    private static final String LOGOS_PUBLIC_BASE_URL = "http://localhost/logos";

    @Mock
    private S3Client s3Client;

    private S3ObjectStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new S3ObjectStorageServiceImpl(s3Client);
        ReflectionTestUtils.setField(service, "rawBucket", RAW_BUCKET);
        ReflectionTestUtils.setField(service, "flyersBucket", FLYERS_BUCKET);
        ReflectionTestUtils.setField(service, "flyersPublicBaseUrl", FLYERS_PUBLIC_BASE_URL);
        ReflectionTestUtils.setField(service, "logosBucket", LOGOS_BUCKET);
        ReflectionTestUtils.setField(service, "logosPublicBaseUrl", LOGOS_PUBLIC_BASE_URL);
    }

    @Test
    void uploadEventFlyerRawStoresJpegInRawBucketUnderFixedKey() {
        UUID eventId = UUID.randomUUID();

        service.uploadEventFlyerRaw(eventId, JPEG_BYTES);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(RAW_BUCKET);
        assertThat(request.key()).isEqualTo(eventId.toString());
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
    void uploadEventFlyerFinalStoresWebpInFlyersBucketAndReturnsPublicUrl() {
        UUID eventId = UUID.randomUUID();
        byte[] webpBytes = {1, 2, 3};

        String url = service.uploadEventFlyerFinal(eventId, webpBytes);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(FLYERS_BUCKET);
        assertThat(request.key()).isEqualTo(eventId.toString());
        assertThat(request.contentType()).isEqualTo("image/webp");
        assertThat(url).isEqualTo(FLYERS_PUBLIC_BASE_URL + "/" + eventId);
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
        assertThat(captor.getValue().key()).isEqualTo(eventId.toString());
    }

    @Test
    void deleteEventFlyerRawWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.deleteEventFlyerRaw(UUID.randomUUID()))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void deleteEventFlyerFinalRemovesObjectFromFlyersBucket() {
        UUID eventId = UUID.randomUUID();

        service.deleteEventFlyerFinal(eventId);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(FLYERS_BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(eventId.toString());
    }

    @Test
    void deleteEventFlyerFinalWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.deleteEventFlyerFinal(UUID.randomUUID()))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void uploadVenueLogoStoresWebpInLogosBucketUnderVenuePrefixAndReturnsPublicUrl() {
        UUID venueId = UUID.randomUUID();

        String url = service.uploadVenueLogo(venueId, new byte[]{1, 2, 3});

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(LOGOS_BUCKET);
        assertThat(request.key()).matches("venues/" + venueId + "/logo-[0-9a-f-]{36}\\.webp");
        assertThat(request.contentType()).isEqualTo("image/webp");
        assertThat(url).isEqualTo(LOGOS_PUBLIC_BASE_URL + "/" + request.key());
    }

    @Test
    void uploadVenueLogoUsesNewKeyOnEachUpload() {
        UUID venueId = UUID.randomUUID();

        String firstUrl = service.uploadVenueLogo(venueId, new byte[]{1});
        String secondUrl = service.uploadVenueLogo(venueId, new byte[]{2});

        assertThat(secondUrl).isNotEqualTo(firstUrl);
    }

    @Test
    void uploadVenueLogoWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> service.uploadVenueLogo(UUID.randomUUID(), new byte[]{1, 2, 3}))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    void deleteVenueLogoRemovesObjectFromLogosBucket() {
        String key = "venues/" + UUID.randomUUID() + "/logo-" + UUID.randomUUID() + ".webp";

        service.deleteVenueLogo(LOGOS_PUBLIC_BASE_URL + "/" + key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(LOGOS_BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void deleteVenueLogoIgnoresUrlsOutsideLogosBucket() {
        service.deleteVenueLogo("https://scontent.cdninstagram.com/logo.jpg");
        service.deleteVenueLogo(FLYERS_PUBLIC_BASE_URL + "/" + UUID.randomUUID());
        service.deleteVenueLogo(null);

        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteVenueLogoWrapsS3FailuresAsMediaStorageException() {
        doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.deleteVenueLogo(LOGOS_PUBLIC_BASE_URL + "/venues/x/logo-y.webp"))
                .isInstanceOf(MediaStorageException.class);
    }
}
