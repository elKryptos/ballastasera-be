package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleDto;
import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserProfileDto;
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

    @Mapping(target = "userId", source = "id")
    UserDto toDto(Users user);

    /** Los estilos no: los sincroniza UsersServiceImpl.syncDanceStyles por diferencia. */
    @Mapping(target = "whatsappVisibility", defaultValue = "EVERYONE")
    @Mapping(target = "danceStyles", ignore = true)
    void updateProfileFromDto(UserUpdateDto dto, @MappingTarget Users user);

    /** Lo usa MapStruct para la coleccion danceStyles de UserDto y UserProfileDto. */
    @Mapping(target = "danceStyleId", source = "danceStyle.id")
    @Mapping(target = "name", source = "danceStyle.name")
    @Mapping(target = "slug", source = "danceStyle.slug")
    UserDanceStyleDto toDanceStyleDto(UserDanceStyles userDanceStyle);

    /** Sin whatsapp: lo decide toProfileDto segun quien mira. */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "age", expression = "java(toAge(user.getBirthDate()))")
    @Mapping(target = "whatsapp", ignore = true)
    UserProfileDto toProfileDtoBase(Users user);

    /** El dueño siempre ve su WhatsApp; los demas solo si la visibilidad es EVERYONE. */
    default UserProfileDto toProfileDto(Users user, UUID viewerId) {
        UserProfileDto dto = toProfileDtoBase(user);
        boolean isOwner = user.getId().equals(viewerId);
        if (isOwner || user.getWhatsappVisibility() == ContactVisibility.EVERYONE) {
            dto.setWhatsapp(user.getWhatsapp());
        }
        return dto;
    }

    default Integer toAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}