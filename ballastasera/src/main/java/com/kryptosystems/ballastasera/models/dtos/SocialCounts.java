package com.kryptosystems.ballastasera.models.dtos;

import java.time.OffsetDateTime;

/**
 * Contadores del perfil y traguardi. No se devuelve tal cual: UserMapper los copia en UserDto y UserProfileDto.
 * events = serate pasadas a las que dijo «Ci vado»; venues = locales distintos de esas serate.
 * firstEventAt = cuando empezo la primera (null si no hay): solo va en UserDto, a los demas no se les dice la fecha.
 */
public record SocialCounts(long followers, long following, long events, long venues, OffsetDateTime firstEventAt) {
}
