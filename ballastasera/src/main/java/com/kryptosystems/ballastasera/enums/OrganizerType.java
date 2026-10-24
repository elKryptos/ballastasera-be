package com.kryptosystems.ballastasera.enums;

public enum OrganizerType {
    PERSON,
    VENUE,
    CLUB,
    SCHOOL,
    ASSOCIATION;

    /** D11: es necesario al menos un venue para ser aprobado. */
    public boolean requiresVenue() {
        return this == VENUE || this == CLUB || this == SCHOOL;
    }

    /** D12: puede publicar eventos solo en su venue. */
    public boolean isVenueBound() {
        return this == VENUE || this == CLUB;
    }
}
