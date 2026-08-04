package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.enums.EventStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Events {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private Organizers organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venues venue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private EventSeries series;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private Cities city;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "flyer_url")
    private String flyerUrl;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "is_free", nullable = false)
    private boolean isFree = true;

    @Column(name = "price", precision = 8, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency = "EUR";

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "event_status")
    private EventStatus status = EventStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany
    @JoinTable(
            name = "event_dance_styles",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "dance_style_id")
    )
    private Set<DanceStyles> danceStyles = new HashSet<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Favorites> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventAttendance> eventAttendances = new ArrayList<>();

}