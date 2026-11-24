package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.AvatarSource;
import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import com.kryptosystems.ballastasera.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UserDto {
    private UUID userId;
    private String email;
    private String displayName;
    private UserRole role;
    private String avatarUrl;
    private String instagram;
    private boolean showProfilePublic;
    private int loginCount;
    private String whatsapp;
    private ContactVisibility whatsappVisibility;
    private LocalDate birthDate;
    private Gender gender;
    private DanceRole danceRole;
    private List<UserDanceStyleDto> danceStyles;
    /** Para la opcion «Usa la foto di Google» de Modifica profilo, aunque se muestre otra. */
    private String googleAvatarUrl;
    private AvatarSource avatarSource;
    private String bio;
    private ContactVisibility attendanceVisibility;
    /** Escuela enlazada: id, nombre y slug del organizer. Escrita a mano: solo schoolName. */
    private UUID schoolOrganizerId;
    private String schoolName;
    private String schoolSlug;
    private String originCountry;
    private HomeCityDto homeCity;
    private boolean showHomeCity;
    /** «Ricevi email». No va en PATCH /auth/me: se cambia con PATCH /users/me/email-notifications. */
    private boolean emailNotifications;
    /** Alias de /@username, null si no eligio ninguno. No va en PATCH /auth/me: se cambia con PATCH /users/me/username. */
    private String username;
    /** «Su ballastasera da…»: la fecha de registro. */
    private OffsetDateTime memberSince;
    private long followersCount;
    private long followingCount;
    private long eventsCount;
    /** Traguardo «8 locali»: locales distintos de las serate de eventsCount. */
    private long venuesCount;
    /** Traguardo «Prima serata»: cuando empezo la primera de eventsCount, null si no hay. Solo aqui, no en el perfil publico. */
    private OffsetDateTime firstEventAt;
}