package com.kryptosystems.ballastasera.models.mappers;

/**
 * Los mappers que lo ponen en su uses (VenuesMapper, OrganizerRequestMapper) lo aplican a cada propiedad
 * String→String: "" o "   " se guardan como null y el resto sin espacios en los extremos.
 * Va en su propia clase y no como default de VenuesMapper porque uses no es transitivo: así un mapper que
 * usa VenuesMapper solo para el venue (EventsMapper) no hereda también la normalización de sus Strings.
 * Estático: MapStruct lo llama directamente, sin bean de Spring (y Mappers.getMapper funciona en los tests).
 */
public final class BlankStrings {

    private BlankStrings() {
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
