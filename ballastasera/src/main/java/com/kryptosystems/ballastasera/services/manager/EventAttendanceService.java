package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;

import java.util.List;
import java.util.UUID;

public interface EventAttendanceService {
    List<EventAttendance> findByUserId(UUID userId);
    List<EventAttendance> findByEventId(UUID eventId);
    EventAttendance save(EventAttendance attendance);
    void deleteById(UserEventId id);
}
