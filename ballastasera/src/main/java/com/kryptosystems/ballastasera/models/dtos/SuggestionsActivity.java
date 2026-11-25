package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Users;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sugerencias de la pestaña Scopri, ya ordenadas, con lo necesario para el «por que». No se devuelve tal cual:
 * FeedMapper lo copia en SuggestionDto, como ProfileActivity en UserProfileDto.
 * <p>
 * mutualFriendsCounts: amigos en comun con quien mira, por id (sin entrada = 0).
 * sharedEvents: la serata mas cercana a la que van los dos, por id (sin entrada = no comparten ninguna).
 * followerIds: quienes ya siguen a quien mira (relation FOLLOWED_BY, boton «Ricambia»).
 */
public record SuggestionsActivity(List<Users> users,
                                  Map<UUID, Long> mutualFriendsCounts,
                                  Map<UUID, EventAttendance> sharedEvents,
                                  Set<UUID> followerIds) {
}
