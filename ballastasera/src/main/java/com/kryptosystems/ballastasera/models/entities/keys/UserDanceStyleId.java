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
public class UserDanceStyleId implements Serializable {
    private UUID userId;
    private Long danceStyleId;
}
