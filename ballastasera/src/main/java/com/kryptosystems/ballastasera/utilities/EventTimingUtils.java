package com.kryptosystems.ballastasera.utilities;

import com.kryptosystems.ballastasera.models.entities.Events;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventTimingUtils {

    /**
     * Una serata sin end_at dura esto. Las queries nativas dicen lo mismo con
     * COALESCE(end_at, start_at + INTERVAL '4 hours'); las JPQL reciben now - DEFAULT_DURATION como parametro.
     */
    public static final Duration DEFAULT_DURATION = Duration.ofHours(4);

    public static boolean isLiveNow(Events event, OffsetDateTime now) {
        return isLiveNow(event.getStartAt(), event.getEndAt(), now);
    }

    public static boolean isLiveNow(OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime now) {
        return !now.isBefore(startAt) && now.isBefore(effectiveEnd(startAt, endAt));
    }

    /** Ya termino: end_at, o start_at + DEFAULT_DURATION si no tiene, no es posterior a now. */
    public static boolean hasEnded(Events event, OffsetDateTime now) {
        return !effectiveEnd(event.getStartAt(), event.getEndAt()).isAfter(now);
    }

    private static OffsetDateTime effectiveEnd(OffsetDateTime startAt, OffsetDateTime endAt) {
        return endAt != null ? endAt : startAt.plus(DEFAULT_DURATION);
    }
}
