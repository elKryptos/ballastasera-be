package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.models.dtos.AttendeeDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import com.kryptosystems.ballastasera.models.mappers.AttendeeMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventAttendanceServiceImpl implements EventAttendanceService {

    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsRepository eventsRepository;
    private final UsersRepository usersRepository;
    private final AttendeeMapper attendeeMapper;

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

    @Override
    public Page<AttendeeDto> findPublicGoingAttendees(UUID eventId, Pageable pageable) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }

        return eventAttendanceRepository.findByEventIdAndStatusAndUserShowProfilePublicTrue(eventId, AttendanceStatus.GOING, pageable)
                .map(ea -> attendeeMapper.toDto(ea.getUser()));
    }

    @Override
    @Transactional
    public EventAttendance setAttendance(UUID userId, UUID eventId, AttendanceStatus status) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }

        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);

        EventAttendance attendance = eventAttendanceRepository.findById(id)
                .orElseGet(() -> {
                    EventAttendance ea = new EventAttendance();
                    ea.setUser(usersRepository.getReferenceById(userId));
                    ea.setEvent(eventsRepository.getReferenceById(eventId));
                    return ea;
                });
        attendance.setStatus(status);
        return eventAttendanceRepository.save(attendance);
    }

    @Override
    public void removeAttendance(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        eventAttendanceRepository.deleteById(id);
    }
}