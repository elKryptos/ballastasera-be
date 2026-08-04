package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "favorites")
@Getter
@Setter
public class Favorites {
    @EmbeddedId
    private UserEventId id =  new UserEventId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Events event;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
