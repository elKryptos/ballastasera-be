package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.models.entities.keys.UserBlockId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** blocker bloqueo a blocked. El efecto es en los dos sentidos: ninguno ve ni sigue al otro. */
@Entity
@Table(name = "user_blocks")
@Getter
@Setter
public class UserBlocks {
    @EmbeddedId
    private UserBlockId id = new UserBlockId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockerId")
    @JoinColumn(name = "blocker_id")
    private Users blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockedId")
    @JoinColumn(name = "blocked_id")
    private Users blocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
