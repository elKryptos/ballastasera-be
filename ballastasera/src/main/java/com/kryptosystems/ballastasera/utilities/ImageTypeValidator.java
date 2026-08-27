package com.kryptosystems.ballastasera.utilities;

import com.kryptosystems.ballastasera.exceptions.InvalidMediaTypeException;

public final class ImageTypeValidator {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageTypeValidator() {
    }

    public static String detectExtension(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidMediaTypeException("File is not a valid JPEG, PNG or WebP image");
        }
        if (content.length >= JPEG_MAGIC.length && startsWith(content, JPEG_MAGIC)) {
            return "jpg";
        }
        if (content.length >= PNG_MAGIC.length && startsWith(content, PNG_MAGIC)) {
            return "png";
        }
        if (content.length >= 12 && isWebP(content)) {
            return "webp";
        }
        throw new InvalidMediaTypeException("File is not a valid JPEG, PNG or WebP image");
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebP(byte[] content) {
        return content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }
}
