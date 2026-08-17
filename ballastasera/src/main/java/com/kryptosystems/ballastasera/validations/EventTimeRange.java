package com.kryptosystems.ballastasera.validations;

import java.time.OffsetDateTime;

public interface EventTimeRange {
    OffsetDateTime getStartAt();
    OffsetDateTime getEndAt();
}
