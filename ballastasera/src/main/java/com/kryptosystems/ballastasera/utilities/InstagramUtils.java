package com.kryptosystems.ballastasera.utilities;

public final class InstagramUtils {

    private InstagramUtils() {
    }

    /** Normaliza un handle de Instagram quitando el "@" inicial si lo trae. */
    public static String normalizeHandle(String instagram) {
        if (instagram == null || instagram.isBlank()) {
            return null;
        }
        String trimmed = instagram.trim();
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }
}