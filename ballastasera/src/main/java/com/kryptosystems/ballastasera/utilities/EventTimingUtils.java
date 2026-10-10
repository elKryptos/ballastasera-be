package com.kryptosystems.ballastasera.utilities;

import com.kryptosystems.ballastasera.models.entities.Events;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventTimingUtils {

    public static boolean isLiveNow(Events event, OffsetDateTime now) {
        return isLiveNow(event.getStartAt(), event.getEndAt(), now);
    }

    public static boolean isLiveNow(OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime now) {
        OffsetDateTime effectiveEnd = endAt != null
                ? endAt
                : startAt.plusHours(4);
        return !now.isBefore(startAt) && now.isBefore(effectiveEnd);
    }
}
