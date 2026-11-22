package com.kryptosystems.ballastasera.models.dtos;

/**
 * Contadores del perfil. No se devuelve tal cual: UserMapper los copia en UserDto y UserProfileDto.
 * events = serate pasadas a las que dijo «Ci vado».
 */
public record SocialCounts(long followers, long following, long events) {
}
