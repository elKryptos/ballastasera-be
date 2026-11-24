package com.kryptosystems.ballastasera.enums;

/** Estado de cada amigo en el dialogo «Invita a ballare» de una serata. Solo INVITABLE tiene el boton activo. */
public enum InviteFriendStatus {
    INVITABLE,
    ALREADY_GOING,      // dijo «Ci vado» (solo si sus serate no estan ocultas a todos)
    ALREADY_INVITED,    // quien mira ya lo invito a esta serata
    PRIVATE             // perfil privado: no se lo puede invitar
}
