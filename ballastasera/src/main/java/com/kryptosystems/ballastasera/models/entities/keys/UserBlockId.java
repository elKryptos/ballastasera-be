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
public class UserBlockId implements Serializable {
    private UUID blockerId;
    private UUID blockedId;

    /**
     * Crea la clave (blocker_id, blocked_id) en una linea, en vez de new + los dos setters.
     * El orden de los parametros importa: of(ana, luca) = «Ana bloqueo a Luca».
     * Uso: userBlocksRepository.deleteById(UserBlockId.of(ana, luca)).
     */
    public static UserBlockId of(UUID blockerId, UUID blockedId) {
        UserBlockId id = new UserBlockId();
        id.setBlockerId(blockerId);
        id.setBlockedId(blockedId);
        return id;
    }
}
