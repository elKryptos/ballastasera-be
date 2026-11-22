package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.UserFollows;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que la pestaña Attività de quien mira necesita, calculado en el momento (no hay historial). No se devuelve
 * tal cual: FeedMapper lo copia en FeedDto, como ProfileActivity en UserProfileDto.
 * <p>
 * tonight: «Ci vado» de la gente que sigue a serate de esta noche, ordenados por serata.
 * goingSoon: «Ci vado» recientes a serate de los proximos dias. newFollowers: quienes empezaron a seguirlo.
 * myEventIds: de esas serate, a cuales va quien mira («ci vai anche tu»).
 * friendIds: de esas personas, quienes son amigos de quien mira (relation MUTUAL).
 */
public record FeedActivity(List<EventAttendance> tonight,
                           List<EventAttendance> goingSoon,
                           List<UserFollows> newFollowers,
                           Set<UUID> myEventIds,
                           Set<UUID> friendIds) {
}
