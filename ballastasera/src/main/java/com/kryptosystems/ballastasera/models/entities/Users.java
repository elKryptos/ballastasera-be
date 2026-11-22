package com.kryptosystems.ballastasera.models.entities;

import com.kryptosystems.ballastasera.enums.AvatarSource;
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

    /** La foto que se muestra, ya resuelta segun avatarSource. Las queries y los DTOs leen solo esta. */
    @Column(name = "avatar_url")
    private String avatarUrl;

    /** La de Google: se actualiza en cada login (CustomOidcUserService), aunque no sea la que se muestra. */
    @Column(name = "google_avatar_url")
    private String googleAvatarUrl;

    /** La subida por el usuario (bucket de logos, prefijo avatars/). */
    @Column(name = "custom_avatar_url")
    private String customAvatarUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "avatar_source", nullable = false, columnDefinition = "avatar_source")
    private AvatarSource avatarSource = AvatarSource.GOOGLE;

    /** Max. 150 caracteres. */
    @Column(name = "bio")
    private String bio;

    /** Quien ve las serate a las que va, en «Chi ci va» y en su perfil. Sigue contando en goingCount. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "attendance_visibility", nullable = false, columnDefinition = "contact_visibility")
    private ContactVisibility attendanceVisibility = ContactVisibility.EVERYONE;

    /** Escuela enlazada (organizer de tipo SCHOOL). Si no esta en la app, schoolName con texto libre. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_organizer_id")
    private Organizers schoolOrganizer;

    @Column(name = "school_name")
    private String schoolName;

    /** ISO 3166-1 alpha-2 (CO, VE, IT...). Publico en el perfil. */
    @Column(name = "origin_country", columnDefinition = "char(2)")
    private String originCountry;

    /** Ciudad donde vive, normalizada con Photon (citta-plan.md). Privada salvo showHomeCity. */
    @Column(name = "home_city")
    private String homeCity;

    @Column(name = "home_region")
    private String homeRegion;

    @Column(name = "home_country", columnDefinition = "char(2)")
    private String homeCountry;

    @Column(name = "home_latitude")
    private Double homeLatitude;

    @Column(name = "home_longitude")
    private Double homeLongitude;

    /** «Mostra la città nel profilo»: el perfil publico muestra solo homeCity, nunca region ni coordenadas. */
    @Column(name = "show_home_city", nullable = false)
    private boolean showHomeCity = false;

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
