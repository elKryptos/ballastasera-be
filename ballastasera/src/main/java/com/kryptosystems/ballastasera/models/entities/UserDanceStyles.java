package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.enums.DanceLevel;
import com.kryptosystems.ballastasera.models.entities.keys.UserDanceStyleId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Estilo de baile que le gusta al usuario, con su nivel en ese estilo. */
@Entity
@Table(name = "user_dance_styles")
@Getter
@Setter
public class UserDanceStyles {
    @EmbeddedId
    private UserDanceStyleId id = new UserDanceStyleId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("danceStyleId")
    @JoinColumn(name = "dance_style_id")
    private DanceStyles danceStyle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "level", nullable = false, columnDefinition = "dance_level")
    private DanceLevel level;
}
