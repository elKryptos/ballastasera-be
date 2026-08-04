package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventAttendanceServiceImpl implements EventAttendanceService {

    private final EventAttendanceRepository eventAttendanceRepository;

    @Override
    public List<EventAttendance> findByUserId(UUID userId) {
        return eventAttendanceRepository.findByUserId(userId);
    }

    @Override
    public List<EventAttendance> findByEventId(UUID eventId) {
        return eventAttendanceRepository.findByEventId(eventId);
    }

    @Override
    public EventAttendance save(EventAttendance attendance) {
        return eventAttendanceRepository.save(attendance);
    }

    @Override
    public void deleteById(UserEventId id) {
        eventAttendanceRepository.deleteById(id);
    }
}
