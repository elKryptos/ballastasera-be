package com.kryptosystems.ballastasera.services.implementations;

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
    public Page<AttendeeDto> findPublicAttendees(UUID eventId, Pageable pageable) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }

        return eventAttendanceRepository.findByEventIdAndStatusAndUserShowProfilePublicTrue(eventId, pageable)
                .map(ea -> attendeeMapper.toDto(ea.getUser()));
    }

    @Override
    @Transactional
    public void addAttendance(UUID userId, UUID eventId) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        if (eventAttendanceRepository.existsById(id)) {
            return;
        }
        EventAttendance attendance = new EventAttendance();
        attendance.setUser(usersRepository.getReferenceById(userId));
        attendance.setEvent(eventsRepository.getReferenceById(eventId));
        eventAttendanceRepository.save(attendance);
        eventsRepository.incrementGoingCount(eventId);
    }

    @Override
    @Transactional
    public void removeAttendance(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        if (eventAttendanceRepository.existsById(id)) {
            eventAttendanceRepository.deleteById(id);
            eventsRepository.decrementGoingCount(eventId);
        }
    }

    @Override
    public boolean isGoing(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        return eventAttendanceRepository.existsById(id);
    }
}