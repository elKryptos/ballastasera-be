package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.AttendeeDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface EventAttendanceService {
    List<EventAttendance> findByUserId(UUID userId);
    List<EventAttendance> findByEventId(UUID eventId);
    EventAttendance save(EventAttendance attendance);
    void deleteById(UserEventId id);

    /** Asistentes con status GOING que optaron por mostrar su perfil. */
    Page<AttendeeDto> findPublicAttendees(UUID eventId, Pageable pageable);

    /** Crea o actualiza el "voy"/"me interesa" del usuario logueado para el evento. */
    void addAttendance(UUID userId, UUID eventId);

    /** Quita al usuario logueado de la lista de asistentes del evento. */
    void removeAttendance(UUID userId, UUID eventId);

    boolean isGoing(UUID userId, UUID eventId);
}