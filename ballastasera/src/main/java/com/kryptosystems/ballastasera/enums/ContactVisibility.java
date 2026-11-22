package com.kryptosystems.ballastasera.enums;

/**
 * Quien ve el WhatsApp en el perfil publico. MUTUALS = solo los amigos (os seguis los dos).
 * Con menos de 18 años nadie lo ve, sea cual sea el valor (UserMapper.toProfileDto).
 */
public enum ContactVisibility {
    NOBODY,
    EVERYONE,
    MUTUALS
}
