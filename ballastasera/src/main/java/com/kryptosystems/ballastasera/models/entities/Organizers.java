package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.enums.OrganizerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "organizers")
@Getter
@Setter
public class Organizers {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "organizer_type")
    private OrganizerType type = OrganizerType.PERSON;

    @Column(name = "description")
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "website")
    private String website;

    @Column(name = "phone")
    private String phone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "facebook")
    private String facebook;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    /** D15. true: llegó por una CLAIM (la página la creó el admin y un usuario la reclamó; se puede soltar).
     * false con user: la creó su dueño con una NEW (solo se borra, D13). false sin user: del admin, sin dueño
     * (sale en claimable). Quien asigne un dueño a una página del admin tiene que poner true. */
    @Column(name = "claimed", nullable = false)
    private boolean claimed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "organizer")
    private List<Venues> venues = new ArrayList<>();

    @OneToMany(mappedBy = "organizer")
    private List<EventSeries> eventSeries = new ArrayList<>();

    @OneToMany(mappedBy = "organizer")
    private List<Events>  events = new ArrayList<>();
}
