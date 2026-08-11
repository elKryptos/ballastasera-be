package com.kryptosystems.ballastasera.utilities;

import java.text.Normalizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+"); //puede que cause problemas el regex, controlar
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private SlugUtils() {}

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        String slug = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
        return EDGE_DASHES.matcher(slug).replaceAll("");
    }

    /** Slugifica input y le agrega "-1", "-2"... hasta que exists() de false.
     * exists suele ser un repo::findBySlug reducido a boolean, ej.:
     * slug -> organizersRepository.findBySlug(slug).isPresent() */
    public static String uniqueSlug(String input, Predicate<String> exists) {
        String base = slugify(input);
        String slug = base;
        int attempt = 1;
        while (exists.test(slug)) {
            slug = base + "-" + attempt++;
        }
        return slug;
    }
}
