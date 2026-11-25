package com.kryptosystems.ballastasera.models.entities.keys;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class UserEventId implements Serializable {
    private UUID userId;
    private UUID eventId;

    /**
     * Crea la clave (user_id, event_id) de «Ci vado» y de favoritos en una linea, como UserFollowId.of.
     * Uso: eventAttendanceRepository.existsById(UserEventId.of(userId, eventId)).
     */
    public static UserEventId of(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        return id;
    }
}
