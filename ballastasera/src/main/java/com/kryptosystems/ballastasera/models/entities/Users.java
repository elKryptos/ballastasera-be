package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import com.kryptosystems.ballastasera.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "show_profile_public", nullable = false)
    private boolean showProfilePublic = true;

    /** Formato E.164 (+393331234567). Quien lo ve lo decide whatsappVisibility. */
    @Column(name = "whatsapp")
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "whatsapp_visibility", nullable = false, columnDefinition = "contact_visibility")
    private ContactVisibility whatsappVisibility = ContactVisibility.MUTUALS;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", columnDefinition = "gender")
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "dance_role", columnDefinition = "dance_role")
    private DanceRole danceRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    private UserRole role = UserRole.USER;

    @Column(name = "is_banned",  nullable = false)
    private boolean isBanned = false;

    @Column(name ="is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name ="deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "login_count", nullable = false)
    private int loginCount = 0;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at",  nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventAttendance> eventAttendance = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Organizers> organizers = new ArrayList<>();

    @OneToMany(mappedBy = "createdBy")
    private List<Venues> createdVenues = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Favorites> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<OrganizerRequests> organizerRequests = new ArrayList<>();

    /** Set y no List: UsersServiceImpl.syncDanceStyles lo sincroniza por diferencia. */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserDanceStyles> danceStyles = new HashSet<>();
}
