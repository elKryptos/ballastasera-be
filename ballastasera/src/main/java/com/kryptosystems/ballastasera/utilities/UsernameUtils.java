package com.kryptosystems.ballastasera.utilities;

import java.util.Set;

/** Reglas del @username (social fase 7). Las usan UsernameUpdateDto (@Pattern) y UsersServiceImpl.checkUsername. */
public final class UsernameUtils {

    /**
     * Minúsculas, números, punto y guion bajo, de 3 a 30; sin punto al principio ni al final ni dos seguidos, como en
     * Instagram. Es la única validación del formato: users.username es VARCHAR(30) UNIQUE, sin CHECK.
     */
    public static final String REGEX = "^(?!\\.)(?!.*\\.\\.)(?!.*\\.$)[a-z0-9._]{3,30}$";

    /** Nombres que nadie puede elegir, para que nadie se haga pasar por ballastasera. */
    public static final Set<String> RESERVED = Set.of("admin", "ballastasera", "staff", "support", "help", "info");

    private UsernameUtils() {
    }

    public static boolean isValid(String username) {
        return username != null && username.matches(REGEX);
    }

    public static boolean isReserved(String username) {
        return RESERVED.contains(username);
    }
}
