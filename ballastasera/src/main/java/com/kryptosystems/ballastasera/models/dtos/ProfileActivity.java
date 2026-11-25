package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Users;

import java.util.List;

/**
 * Lo que el perfil de GET /users/{id} muestra de la actividad del dueño para quien lo mira. No se devuelve tal
 * cual: UserMapper lo copia en UserProfileDto, como SocialCounts.
 * <p>
 * «Avete in comune»: hasta 3 amigos en comun con su total, serate pasadas a las que fueron los dos y la ultima.
 * lookingForPartnerToday: marco «Cerco qualcuno» en una serata en curso o de las proximas 24 horas.
 * Las serate y el pallino solo si quien mira puede ver las serate del dueño (attendanceVisibility).
 */
public record ProfileActivity(List<Users> mutualFriends,
                              long mutualFriendsCount,
                              long eventsTogetherCount,
                              Events lastEventTogether,
                              boolean lookingForPartnerToday) {
}
