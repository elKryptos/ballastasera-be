package com.kryptosystems.ballastasera.utilities;

import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageTypeValidatorTest {

    private static final byte[] JPEG_BYTES =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    private static final byte[] WEBP_BYTES =
            {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
    private static final byte[] GIF_BYTES =
            {'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00, 0x00, 0x00};

    @Test
    void detectsJpegByMagicBytes() {
        assertThat(ImageTypeValidator.detectExtension(JPEG_BYTES)).isEqualTo("jpg");
    }

    @Test
    void detectsPngByMagicBytes() {
        assertThat(ImageTypeValidator.detectExtension(PNG_BYTES)).isEqualTo("png");
    }

    @Test
    void detectsWebpByMagicBytes() {
        assertThat(ImageTypeValidator.detectExtension(WEBP_BYTES)).isEqualTo("webp");
    }

    @Test
    void rejectsNonImageContentRegardlessOfItsNameOrContentType() {
        assertThatThrownBy(() -> ImageTypeValidator.detectExtension(GIF_BYTES))
                .isInstanceOf(InvalidMediaTypeException.class);
    }

    @Test
    void rejectsEmptyOrNullContent() {
        assertThatThrownBy(() -> ImageTypeValidator.detectExtension(new byte[0]))
                .isInstanceOf(InvalidMediaTypeException.class);
        assertThatThrownBy(() -> ImageTypeValidator.detectExtension(null))
                .isInstanceOf(InvalidMediaTypeException.class);
    }
}
