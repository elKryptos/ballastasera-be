package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Perfil publico de GET /users/{id}. Solo la edad, nunca la fecha de nacimiento.
 * whatsapp es null si el dueño no lo hizo visible para quien mira (UserMapper.toProfileDto).
 * relation y los contadores dependen de quien mira: no pasan por ninguna cache.
 */
@Getter
@Setter
public class UserProfileDto {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String instagram;
    private Integer age;
    private Gender gender;
    private DanceRole danceRole;
    private String whatsapp;
    private List<UserDanceStyleDto> danceStyles;
    private String bio;
    /** Escuela enlazada: id, nombre y slug del organizer (para ir a su pagina). Escrita a mano: solo schoolName. */
    private UUID schoolOrganizerId;
    private String schoolName;
    private String schoolSlug;
    private String originCountry;
    /** Solo el nombre de la ciudad, y solo si el dueño activo «Mostra la città nel profilo». */
    private String homeCityName;
    private SocialRelation relation;
    private long followersCount;
    private long followingCount;
    private long eventsCount;
    /** «Avete in comune»: hasta 3 amigos en comun (relation MUTUAL: son amigos de quien mira) y su total. */
    private List<UserSummaryDto> mutualFriends;
    private long mutualFriendsCount;
    /** Serate pasadas a las que fueron los dos, y la ultima. 0 y null si sus serate no son visibles para quien mira. */
    private long eventsTogetherCount;
    private UUID lastEventTogetherId;
    private String lastEventTogetherTitle;
    private OffsetDateTime lastEventTogetherStartAt;
    /** Pallino «Cerco qualcuno con cui ballare»: lo marco en una serata en curso o de las proximas 24 horas. */
    private boolean lookingForPartnerToday;
}
