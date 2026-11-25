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
public class UserFollowId implements Serializable {
    private UUID followerId;
    private UUID followedId;

    /**
     * Crea la clave (follower_id, followed_id) en una linea, en vez de new + los dos setters.
     * El orden de los parametros es el sentido del follow: of(ana, luca) = «Ana sigue a Luca».
     * Uso: userFollowsRepository.existsById(UserFollowId.of(ana, luca)).
     */
    public static UserFollowId of(UUID followerId, UUID followedId) {
        UserFollowId id = new UserFollowId();
        id.setFollowerId(followerId);
        id.setFollowedId(followedId);
        return id;
    }
}
