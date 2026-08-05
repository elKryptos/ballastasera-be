package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "event_attendance")
@Getter
@Setter
public class EventAttendance {
    @EmbeddedId
    private UserEventId id = new UserEventId();

    @ManyToOne(fetch = FetchType.LAZY,  optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY,  optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Events event;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "attendance_status")
    private AttendanceStatus status = AttendanceStatus.INTERESTED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
