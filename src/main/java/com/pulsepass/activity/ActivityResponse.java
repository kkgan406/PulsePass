package com.pulsepass.activity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ActivityResponse(Long id, String name, String description, OffsetDateTime saleStartTime,
    OffsetDateTime saleEndTime, OffsetDateTime eventStartTime, String status, OffsetDateTime createdAt) {
    static ActivityResponse from(Activity a) {
        return new ActivityResponse(a.id, a.name, a.description, a.saleStartTime.atOffset(ZoneOffset.UTC),
            a.saleEndTime.atOffset(ZoneOffset.UTC), a.eventStartTime.atOffset(ZoneOffset.UTC),
            a.status, a.createdAt.atOffset(ZoneOffset.UTC));
    }
}
