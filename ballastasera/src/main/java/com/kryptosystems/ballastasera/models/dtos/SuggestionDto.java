package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una sugerencia de Scopri (GET /users/me/suggestions). El FE arma el «por que» con estos campos:
 * «Bachata Sunday · 3 amici in comune», «Noche Latina · cerca con chi ballare».
 */
@Getter
@Setter
public class SuggestionDto {
    /** relation siempre NONE o FOLLOWED_BY: a quien ya sigue no se le sugiere. */
    private UserSummaryDto user;
    private long mutualFriendsCount;
    /** La serata mas cercana a la que van los dos; null si no comparten ninguna. */
    private UUID eventId;
    private String eventSlug;
    private String eventTitle;
    private OffsetDateTime eventStartAt;
    /** Marco «Cerco qualcuno» en esa serata. */
    private boolean lookingForPartner;
}
