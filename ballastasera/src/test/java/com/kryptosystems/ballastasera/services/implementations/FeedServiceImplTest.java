package com.kryptosystems.ballastasera.services.implementations;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** «Stasera» acaba a las proximas 06:00 hora de Italia, sea cual sea el offset con el que llega now. */
class FeedServiceImplTest {

    @Test
    void inTheEveningTonightEndsTomorrowAtSix() {
        assertThat(FeedServiceImpl.tonightUntil(OffsetDateTime.parse("2026-11-22T23:00:00+01:00")))
                .isEqualTo(OffsetDateTime.parse("2026-11-23T06:00:00+01:00"));
    }

    @Test
    void afterMidnightItIsStillTheSameNight() {
        assertThat(FeedServiceImpl.tonightUntil(OffsetDateTime.parse("2026-11-23T02:00:00+01:00")))
                .isEqualTo(OffsetDateTime.parse("2026-11-23T06:00:00+01:00"));
    }

    @Test
    void atSixANewNightStarts() {
        assertThat(FeedServiceImpl.tonightUntil(OffsetDateTime.parse("2026-11-23T06:00:00+01:00")))
                .isEqualTo(OffsetDateTime.parse("2026-11-24T06:00:00+01:00"));
    }

    /** 22:30 UTC son las 23:30 en Milano: todavia es la noche del 22. */
    @Test
    void usesItalianTimeWhateverTheOffset() {
        assertThat(FeedServiceImpl.tonightUntil(OffsetDateTime.parse("2026-11-22T22:30:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-11-23T06:00:00+01:00"));
    }

    /** Con el horario de verano las 06:00 de Italia son +02:00. */
    @Test
    void inSummerSixIsAtPlusTwo() {
        assertThat(FeedServiceImpl.tonightUntil(OffsetDateTime.parse("2026-07-10T21:00:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-07-11T06:00:00+02:00"));
    }
}
