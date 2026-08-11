package com.kryptosystems.ballastasera.utilities;

import com.kryptosystems.ballastasera.models.entities.Events;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventTimingUtils {

    public static boolean isLiveNow(Events event, OffsetDateTime now) {
        OffsetDateTime effectiveEnd = event.getEndAt() != null
                ? event.getEndAt()
                : event.getStartAt().plusHours(4);
        return !now.isBefore(event.getStartAt()) && now.isBefore(effectiveEnd);
    }
}
