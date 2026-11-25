package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.AttendeesActivity;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventAttendanceService {
    List<EventAttendance> findByUserId(UUID userId);
    List<EventAttendance> findByEventId(UUID eventId);
    EventAttendance save(EventAttendance attendance);
    void deleteById(UserEventId id);

    /** Asistentes que optaron por mostrar su perfil, sin bloqueos con viewerId (null = sin login). */
    AttendeesActivity findPublicAttendees(UUID eventId, UUID viewerId, Pageable pageable);

    /** Crea el "voy" del usuario logueado o, si ya iba, cambia su marca «Cerco qualcuno».
     * lookingForPartner null: al crear queda false; si ya iba, no cambia. */
    void addAttendance(UUID userId, UUID eventId, Boolean lookingForPartner);

    /** El «Ci vado» del usuario a la serata, con su marca «Cerco qualcuno». Vacio si no va. */
    Optional<EventAttendance> findAttendance(UUID userId, UUID eventId);

    /** Amigos de viewerId que van a la serata («Sara e Luca ci vanno»). 404 si el evento no existe. */
    List<Users> findFriendsGoing(UUID viewerId, UUID eventId);

    /** Quita al usuario logueado de la lista de asistentes del evento. */
    void removeAttendance(UUID userId, UUID eventId);

    boolean isGoing(UUID userId, UUID eventId);

    /** Primeros asistentes publicos con foto de cada evento (caritas del mapa/lista). */
    List<EventAttendance> findAvatarPreviews(List<UUID> eventIds);
}