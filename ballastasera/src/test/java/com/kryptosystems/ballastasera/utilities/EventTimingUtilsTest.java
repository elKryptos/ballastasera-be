package com.kryptosystems.ballastasera.utilities;

import com.kryptosystems.ballastasera.models.entities.Events;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EventTimingUtilsTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 11, 28, 22, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void withoutEndAtASerataEndsFourHoursAfterItStarts() {
        Events event = event(null);

        assertThat(EventTimingUtils.hasEnded(event, START.plusHours(3).plusMinutes(59))).isFalse();
        assertThat(EventTimingUtils.hasEnded(event, START.plusHours(4))).isTrue();
    }

    @Test
    void withEndAtTheSerataEndsThen() {
        Events event = event(START.plusHours(6));

        assertThat(EventTimingUtils.hasEnded(event, START.plusHours(5))).isFalse();
        assertThat(EventTimingUtils.hasEnded(event, START.plusHours(6))).isTrue();
    }

    /** Una serata en curso no termino (se puede invitar) y sigue en vivo. */
    @Test
    void aLiveSerataHasNotEnded() {
        Events event = event(null);
        OffsetDateTime now = START.plusHours(1);

        assertThat(EventTimingUtils.isLiveNow(event, now)).isTrue();
        assertThat(EventTimingUtils.hasEnded(event, now)).isFalse();
    }

    private static Events event(OffsetDateTime endAt) {
        Events event = new Events();
        event.setStartAt(START);
        event.setEndAt(endAt);
        return event;
    }
}
