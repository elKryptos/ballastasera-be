package com.kryptosystems.ballastasera.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum EventManagementTab {
    /** No anuladas que aún non han terminado, en curso incluido. */
    UPCOMING(EnumSet.of(EventStatus.DRAFT, EventStatus.PENDING, EventStatus.PUBLISHED), false),
    /** Anuladas que aún no han terminado. */
    CANCELLED(EnumSet.of(EventStatus.CANCELLED), false),
    /** Terminadas, en cualquier estado. */
    PAST(EnumSet.allOf(EventStatus.class), true);

    private final Set<EventStatus> statuses;
    private final boolean ended;
}