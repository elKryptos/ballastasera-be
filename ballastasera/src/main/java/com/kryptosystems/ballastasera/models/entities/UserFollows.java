package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.models.entities.keys.UserFollowId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** follower sigue a followed. Amigos = la fila en los dos sentidos (no hay tabla de amistad). */
@Entity
@Table(name = "user_follows")
@Getter
@Setter
public class UserFollows {
    @EmbeddedId
    private UserFollowId id = new UserFollowId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("followerId")
    @JoinColumn(name = "follower_id")
    private Users follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("followedId")
    @JoinColumn(name = "followed_id")
    private Users followed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
