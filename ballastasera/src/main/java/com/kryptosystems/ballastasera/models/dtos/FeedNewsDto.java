package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.FeedNewsType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila de «Novità». FOLLOWED_YOU: user empezo a seguir a quien mira (relation FOLLOWED_BY → «Ricambia» si
 * user.profilePublic, sin boton si es privado; MUTUAL → «Amici»); los campos event* van null.
 * GOING_TO: user, a quien sigue, dijo «Ci vado» a esa serata.
 */
@Getter
@Setter
public class FeedNewsDto {
    private FeedNewsType type;
    /** Cuando empezo a seguirlo o cuando dijo «Ci vado»: «2 h». */
    private OffsetDateTime at;
    private UserSummaryDto user;
    private UUID eventId;
    private String eventSlug;
    private String eventTitle;
    private OffsetDateTime eventStartAt;
    /** Quien mira tambien va: «ci vai anche tu». */
    private boolean meGoing;
}
