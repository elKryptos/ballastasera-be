package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.InviteFriendStatus;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.CursorPageDto;
import com.kryptosystems.ballastasera.models.dtos.FollowsActivity;
import com.kryptosystems.ballastasera.models.dtos.HomeCityDto;
import com.kryptosystems.ballastasera.models.dtos.InviteFriendDto;
import com.kryptosystems.ballastasera.models.dtos.InviteFriendsActivity;
import com.kryptosystems.ballastasera.models.dtos.InviteFriendsDto;
import com.kryptosystems.ballastasera.models.dtos.ProfileActivity;
import com.kryptosystems.ballastasera.models.dtos.SocialCounts;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleDto;
import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserProfileDto;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.UserDanceStyles;
import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Slice;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sin nullValuePropertyMappingStrategy = IGNORE (a diferencia de OrganizerMapper/EventsMapper):
 * updateProfileFromDto es un reemplazo completo del formulario, un campo null en el DTO deja null en la entidad.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    int ADULT_AGE = 18;

    /** Sin contadores: los pone toDto(user, counts). */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "schoolOrganizerId", source = "schoolOrganizer.id")
    @Mapping(target = "schoolSlug", source = "schoolOrganizer.slug")
    @Mapping(target = "schoolName", expression = "java(toSchoolName(user))")
    @Mapping(target = "homeCity", expression = "java(toHomeCityDto(user))")
    @Mapping(target = "followersCount", ignore = true)
    @Mapping(target = "followingCount", ignore = true)
    @Mapping(target = "eventsCount", ignore = true)
    UserDto toDto(Users user);

    /** /auth/me: el dueño con sus contadores. */
    default UserDto toDto(Users user, SocialCounts counts) {
        UserDto dto = toDto(user);
        dto.setFollowersCount(counts.followers());
        dto.setFollowingCount(counts.following());
        dto.setEventsCount(counts.events());
        return dto;
    }

    /**
     * Los estilos no: los sincroniza UsersServiceImpl.syncDanceStyles por diferencia. La escuela enlazada tampoco:
     * la busca y valida el service (tiene que ser un organizer SCHOOL). homeCity null deja las 5 columnas en null.
     */
    @Mapping(target = "whatsappVisibility", defaultValue = "MUTUALS")
    @Mapping(target = "attendanceVisibility", defaultValue = "EVERYONE")
    @Mapping(target = "showHomeCity", defaultValue = "false")
    @Mapping(target = "danceStyles", ignore = true)
    @Mapping(target = "schoolOrganizer", ignore = true)
    @Mapping(target = "homeCity", source = "homeCity.name")
    @Mapping(target = "homeRegion", source = "homeCity.region")
    @Mapping(target = "homeCountry", source = "homeCity.country")
    @Mapping(target = "homeLatitude", source = "homeCity.latitude")
    @Mapping(target = "homeLongitude", source = "homeCity.longitude")
    void updateProfileFromDto(UserUpdateDto dto, @MappingTarget Users user);

    /** Lo usa MapStruct para la coleccion danceStyles de UserDto y UserProfileDto. */
    @Mapping(target = "danceStyleId", source = "danceStyle.id")
    @Mapping(target = "name", source = "danceStyle.name")
    @Mapping(target = "slug", source = "danceStyle.slug")
    UserDanceStyleDto toDanceStyleDto(UserDanceStyles userDanceStyle);

    /** Sin whatsapp, relacion ni contadores: los decide toProfileDto segun quien mira. */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "age", expression = "java(toAge(user.getBirthDate()))")
    @Mapping(target = "whatsapp", ignore = true)
    @Mapping(target = "schoolOrganizerId", source = "schoolOrganizer.id")
    @Mapping(target = "schoolSlug", source = "schoolOrganizer.slug")
    @Mapping(target = "schoolName", expression = "java(toSchoolName(user))")
    @Mapping(target = "homeCityName", expression = "java(user.isShowHomeCity() ? user.getHomeCity() : null)")
    @Mapping(target = "relation", ignore = true)
    @Mapping(target = "muted", ignore = true)
    @Mapping(target = "followersCount", ignore = true)
    @Mapping(target = "followingCount", ignore = true)
    @Mapping(target = "eventsCount", ignore = true)
    @Mapping(target = "mutualFriends", ignore = true)
    @Mapping(target = "mutualFriendsCount", ignore = true)
    @Mapping(target = "eventsTogetherCount", ignore = true)
    @Mapping(target = "lastEventTogetherId", ignore = true)
    @Mapping(target = "lastEventTogetherTitle", ignore = true)
    @Mapping(target = "lastEventTogetherStartAt", ignore = true)
    @Mapping(target = "lookingForPartnerToday", ignore = true)
    UserProfileDto toProfileDtoBase(Users user);

    /** Perfil de GET /users/{id} visto por viewerId. muted: viewerId lo silencio. */
    default UserProfileDto toProfileDto(Users user, UUID viewerId, SocialRelation relation, boolean muted,
                                        SocialCounts counts, ProfileActivity activity) {
        UserProfileDto dto = toProfileDtoBase(user);
        dto.setRelation(relation);
        dto.setMuted(muted);
        dto.setFollowersCount(counts.followers());
        dto.setFollowingCount(counts.following());
        dto.setEventsCount(counts.events());
        if (canSeeWhatsapp(user, viewerId, relation)) {
            dto.setWhatsapp(user.getWhatsapp());
        }
        // Los amigos en comun son amigos de quien mira: MUTUAL
        dto.setMutualFriends(toSummaryDtos(activity.mutualFriends(), SocialRelation.MUTUAL));
        dto.setMutualFriendsCount(activity.mutualFriendsCount());
        dto.setEventsTogetherCount(activity.eventsTogetherCount());
        if (activity.lastEventTogether() != null) {
            dto.setLastEventTogetherId(activity.lastEventTogether().getId());
            dto.setLastEventTogetherTitle(activity.lastEventTogether().getTitle());
            dto.setLastEventTogetherStartAt(activity.lastEventTogether().getStartAt());
        }
        dto.setLookingForPartnerToday(activity.lookingForPartnerToday());
        return dto;
    }

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "profilePublic", source = "user.showProfilePublic")
    UserSummaryDto toSummaryDto(Users user, SocialRelation relation);

    /** Listas con la misma relacion para todos: bloqueados (NONE), amigos que van a una serata (MUTUAL). */
    default List<UserSummaryDto> toSummaryDtos(List<Users> users, SocialRelation relation) {
        return users.stream().map(user -> toSummaryDto(user, relation)).toList();
    }

    /** /me/followers: cada fila sigue a quien mira. MUTUAL si quien mira lo sigue tambien, si no FOLLOWED_BY. */
    default CursorPageDto<UserSummaryDto> toFollowersCursorPage(FollowsActivity activity) {
        return toFollowsCursorPage(activity, UserFollows::getFollower, person -> toSummaryDto(person,
                SocialRelation.of(activity.friendIds().contains(person.getId()), true)));
    }

    /** /me/following y /me/friends: quien mira sigue a cada fila. MUTUAL si lo sigue tambien, si no FOLLOWING. */
    default CursorPageDto<UserSummaryDto> toFollowingCursorPage(FollowsActivity activity) {
        return toFollowsCursorPage(activity, UserFollows::getFollowed, person -> toSummaryDto(person,
                SocialRelation.of(true, activity.friendIds().contains(person.getId()))));
    }

    /**
     * Dialogo «Invita a ballare»: los amigos (MUTUAL) con su estado. Si hay mas de uno, gana el que explica mejor por
     * que no se lo puede invitar: privado, ya va, ya invitado.
     */
    default InviteFriendsDto toInviteFriendsDto(InviteFriendsActivity activity) {
        InviteFriendsDto dto = new InviteFriendsDto();
        dto.setInvitesLeft(activity.invitesLeft());
        dto.setFriends(toFollowsCursorPage(activity.friends(), UserFollows::getFollowed, friend -> {
            InviteFriendDto row = new InviteFriendDto();
            row.setUser(toSummaryDto(friend, SocialRelation.MUTUAL));
            if (!friend.isShowProfilePublic()) {
                row.setStatus(InviteFriendStatus.PRIVATE);
            } else if (activity.goingIds().contains(friend.getId())) {
                row.setStatus(InviteFriendStatus.ALREADY_GOING);
            } else if (activity.invitedIds().contains(friend.getId())) {
                row.setStatus(InviteFriendStatus.ALREADY_INVITED);
            } else {
                row.setStatus(InviteFriendStatus.INVITABLE);
            }
            return row;
        }));
        return dto;
    }

    /** person: el otro usuario de cada follow; item: su fila. El cursor es la fecha del follow y el id de esa persona. */
    private <T> CursorPageDto<T> toFollowsCursorPage(FollowsActivity activity,
                                                     Function<UserFollows, Users> person,
                                                     Function<Users, T> item) {
        Slice<UserFollows> follows = activity.follows();
        CursorPageDto<T> dto = new CursorPageDto<>();
        dto.setItems(follows.getContent().stream()
                .map(person)
                .map(item)
                .toList());
        dto.setTotal(activity.total());
        if (follows.hasNext() && !follows.isEmpty()) {
            UserFollows last = follows.getContent().getLast();
            dto.setNextCursorStartAt(last.getCreatedAt());
            dto.setNextCursorId(person.apply(last).getId());
        }
        return dto;
    }

    /** Nombre de la escuela enlazada o, si no hay, el escrito a mano. */
    default String toSchoolName(Users user) {
        Organizers organizer = user.getSchoolOrganizer();
        return organizer != null ? organizer.getName() : user.getSchoolName();
    }

    default HomeCityDto toHomeCityDto(Users user) {
        if (user.getHomeCity() == null) {
            return null;
        }
        HomeCityDto city = new HomeCityDto();
        city.setName(user.getHomeCity());
        city.setRegion(user.getHomeRegion());
        city.setCountry(user.getHomeCountry());
        city.setLatitude(user.getHomeLatitude());
        city.setLongitude(user.getHomeLongitude());
        return city;
    }

    default Integer toAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * El dueño siempre ve su WhatsApp. Un menor de 18 no lo muestra a nadie, elija lo que elija.
     * Si no hay fecha de nacimiento, no se sabe la edad y vale la visibilidad elegida.
     */
    private boolean canSeeWhatsapp(Users user, UUID viewerId, SocialRelation relation) {
        if (user.getId().equals(viewerId)) {
            return true;
        }
        Integer age = toAge(user.getBirthDate());
        if (age != null && age < ADULT_AGE) {
            return false;
        }
        return user.getWhatsappVisibility().allows(relation);
    }
}
