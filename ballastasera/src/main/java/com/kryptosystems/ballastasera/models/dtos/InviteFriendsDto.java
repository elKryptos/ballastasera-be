package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

/** GET /events/{id}/invites/friends: «Ti restano N inviti oggi» y los amigos con su estado, paginados como /me/friends. */
@Getter
@Setter
public class InviteFriendsDto {
    private int invitesLeft;
    private CursorPageDto<InviteFriendDto> friends;
}
