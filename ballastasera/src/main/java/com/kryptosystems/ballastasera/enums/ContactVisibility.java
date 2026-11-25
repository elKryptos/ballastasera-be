package com.kryptosystems.ballastasera.enums;

/**
 * Quien ve un dato del perfil (el WhatsApp, las serate a las que va). MUTUALS = solo los amigos (os seguis
 * los dos). El WhatsApp de un menor de 18 no lo ve nadie, sea cual sea el valor (UserMapper.toProfileDto).
 */
public enum ContactVisibility {
    NOBODY,
    EVERYONE,
    MUTUALS;

    /** Si alguien con esta relacion con el dueño puede verlo. El dueño siempre ve lo suyo: eso lo mira quien llama. */
    public boolean allows(SocialRelation relation) {
        return switch (this) {
            case EVERYONE -> true;
            case MUTUALS -> relation == SocialRelation.MUTUAL;
            case NOBODY -> false;
        };
    }
}
