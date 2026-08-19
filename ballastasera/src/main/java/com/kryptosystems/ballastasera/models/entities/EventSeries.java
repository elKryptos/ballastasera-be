package com.kryptosystems.ballastasera.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "event_series")
@Getter
@Setter
public class EventSeries {
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private Cities city;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "rrule")
    private String rrule;

    @Column(name = "description")
    private String description;

    @Column(name = "flyer_url")
    private String flyerUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "whatsapp_url")
    private String whatsappUrl;

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

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany
    @JoinTable(
            name = "event_series_dance_styles",
            joinColumns = @JoinColumn(name = "series_id"),
            inverseJoinColumns = @JoinColumn(name = "dance_style_id")
    )
    private Set<DanceStyles> danceStyles = new HashSet<>();

    @OneToMany(mappedBy = "series")
    private List<Events> events = new ArrayList<>();
}
