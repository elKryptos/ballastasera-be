package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** «Stasera escono»: una serata de esta noche con la gente que sigue quien mira y que va. */
@Getter
@Setter
public class FeedTonightDto {
    private UUID eventId;
    private String eventSlug;
    private String eventTitle;
    private OffsetDateTime eventStartAt;
    private String venueName;
    /** Quien mira tambien va: «ci vai anche tu» en vez del boton «Vado anch'io». */
    private boolean meGoing;
    /** En el orden en que dijeron «Ci vado». relation FOLLOWING o MUTUAL. */
    private List<UserSummaryDto> people;
}
