package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CursorPageDto<T> {
    private List<T> items;
    private OffsetDateTime nextCursorStartAt;
    private UUID nextCursorId;
    private long total;
}
