package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.SocialCounts;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleDto;
import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserProfileDto;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.UserDanceStyles;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Sin nullValuePropertyMappingStrategy = IGNORE (a diferencia de OrganizerMapper/EventsMapper):
 * updateProfileFromDto es un reemplazo completo del formulario, un campo null en el DTO deja null en la entidad.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    int ADULT_AGE = 18;

    /** Sin contadores: los pone toDto(user, counts). */
    @Mapping(target = "userId", source = "id")
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

    /** Los estilos no: los sincroniza UsersServiceImpl.syncDanceStyles por diferencia. */
    @Mapping(target = "whatsappVisibility", defaultValue = "MUTUALS")
    @Mapping(target = "danceStyles", ignore = true)
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
    @Mapping(target = "relation", ignore = true)
    @Mapping(target = "followersCount", ignore = true)
    @Mapping(target = "followingCount", ignore = true)
    @Mapping(target = "eventsCount", ignore = true)
    UserProfileDto toProfileDtoBase(Users user);

    /** Perfil de GET /users/{id} visto por viewerId. */
    default UserProfileDto toProfileDto(Users user, UUID viewerId, SocialRelation relation, SocialCounts counts) {
        UserProfileDto dto = toProfileDtoBase(user);
        dto.setRelation(relation);
        dto.setFollowersCount(counts.followers());
        dto.setFollowingCount(counts.following());
        dto.setEventsCount(counts.events());
        if (canSeeWhatsapp(user, viewerId, relation)) {
            dto.setWhatsapp(user.getWhatsapp());
        }
        return dto;
    }

    @Mapping(target = "userId", source = "user.id")
    UserSummaryDto toSummaryDto(Users user, SocialRelation relation);

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
        return switch (user.getWhatsappVisibility()) {
            case EVERYONE -> true;
            case MUTUALS -> relation == SocialRelation.MUTUAL;
            case NOBODY -> false;
        };
    }
}
