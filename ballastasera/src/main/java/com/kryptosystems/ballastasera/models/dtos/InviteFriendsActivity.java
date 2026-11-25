package com.kryptosystems.ballastasera.models.dtos;

import java.util.Set;
import java.util.UUID;

/**
 * Una pagina del dialogo «Invita a ballare» de una serata. No se devuelve tal cual: UserMapper la copia en
 * InviteFriendsDto, como FollowsActivity.
 * <p>
 * friends: los amigos de quien mira (la misma pagina que /me/friends).
 * invitedIds: de esos amigos, a quienes ya invito a la serata. goingIds: quienes ya dijeron «Ci vado» (sin los que
 * ocultan sus serate a todos). invitesLeft: invitaciones que le quedan en las ultimas 24 horas.
 */
public record InviteFriendsActivity(FollowsActivity friends,
                                    Set<UUID> invitedIds,
                                    Set<UUID> goingIds,
                                    int invitesLeft) {
}
