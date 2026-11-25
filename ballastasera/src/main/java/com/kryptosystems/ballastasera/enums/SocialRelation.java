package com.kryptosystems.ballastasera.enums;

/**
 * Relacion de quien mira con otro usuario, para el boton del perfil:
 * NONE «Segui» · FOLLOWING «Segui già» · FOLLOWED_BY «Ricambia» · MUTUAL «Amici».
 * No se guarda en la DB: sale de user_follows en los dos sentidos.
 */
public enum SocialRelation {
    NONE,
    FOLLOWING,
    FOLLOWED_BY,
    MUTUAL;

    public static SocialRelation of(boolean viewerFollows, boolean followsViewer) {
        if (viewerFollows && followsViewer) {
            return MUTUAL;
        }
        if (viewerFollows) {
            return FOLLOWING;
        }
        return followsViewer ? FOLLOWED_BY : NONE;
    }
}
