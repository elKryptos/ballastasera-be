package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.InviteFriendStatus;
import lombok.Getter;
import lombok.Setter;

/** Una fila del dialogo «Invita a ballare»: el amigo (relation MUTUAL) y si se lo puede invitar a la serata. */
@Getter
@Setter
public class InviteFriendDto {
    private UserSummaryDto user;
    private InviteFriendStatus status;
}
