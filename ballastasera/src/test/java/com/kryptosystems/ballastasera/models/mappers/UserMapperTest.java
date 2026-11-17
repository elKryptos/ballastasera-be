package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.DanceLevel;
import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleDto;
import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserProfileDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.UserDanceStyles;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String WHATSAPP = "+393331234567";

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void profileDtoHidesTheWhatsappFromOthersWhenVisibilityIsNobody() {
        Users user = user();
        user.setWhatsappVisibility(ContactVisibility.NOBODY);

        assertThat(mapper.toProfileDto(user, VIEWER_ID).getWhatsapp()).isNull();
    }

    @Test
    void profileDtoShowsTheWhatsappToOthersWhenVisibilityIsEveryone() {
        Users user = user();
        user.setWhatsappVisibility(ContactVisibility.EVERYONE);

        assertThat(mapper.toProfileDto(user, VIEWER_ID).getWhatsapp()).isEqualTo(WHATSAPP);
    }

    @Test
    void profileDtoAlwaysShowsTheOwnerTheirWhatsapp() {
        Users user = user();
        user.setWhatsappVisibility(ContactVisibility.NOBODY);

        assertThat(mapper.toProfileDto(user, OWNER_ID).getWhatsapp()).isEqualTo(WHATSAPP);
    }

    /** El dia antes de cumplir 31 todavia tiene 30. */
    @Test
    void profileDtoCarriesTheAgeNotTheBirthDate() {
        Users user = user();
        user.setBirthDate(LocalDate.now().minusYears(31).plusDays(1));

        UserProfileDto dto = mapper.toProfileDto(user, VIEWER_ID);

        assertThat(dto.getAge()).isEqualTo(30);
        assertThat(dto.getUserId()).isEqualTo(OWNER_ID);
        assertThat(dto.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(dto.getDanceRole()).isEqualTo(DanceRole.FOLLOWER);
    }

    @Test
    void profileDtoWithoutBirthDateHasNoAge() {
        assertThat(mapper.toProfileDto(user(), VIEWER_ID).getAge()).isNull();
    }

    @Test
    void profileDtoCarriesTheDanceStylesWithNameSlugAndLevel() {
        Users user = user();
        user.getDanceStyles().add(userStyle(user, DanceLevel.ADVANCED));

        UserProfileDto dto = mapper.toProfileDto(user, VIEWER_ID);

        assertThat(dto.getDanceStyles()).singleElement().satisfies(style -> {
            assertThat(style.getDanceStyleId()).isEqualTo(1L);
            assertThat(style.getName()).isEqualTo("Salsa");
            assertThat(style.getSlug()).isEqualTo("salsa");
            assertThat(style.getLevel()).isEqualTo(DanceLevel.ADVANCED);
        });
    }

    /** /auth/me: el dueño ve sus datos tal cual, fecha de nacimiento incluida. */
    @Test
    void meDtoCarriesTheNewProfileFields() {
        Users user = user();
        user.setBirthDate(LocalDate.of(1995, 4, 12));
        user.getDanceStyles().add(userStyle(user, DanceLevel.BEGINNER));

        UserDto dto = mapper.toDto(user);

        assertThat(dto.getWhatsapp()).isEqualTo(WHATSAPP);
        assertThat(dto.getWhatsappVisibility()).isEqualTo(ContactVisibility.EVERYONE);
        assertThat(dto.getBirthDate()).isEqualTo(LocalDate.of(1995, 4, 12));
        assertThat(dto.getDanceStyles()).extracting(UserDanceStyleDto::getName).containsExactly("Salsa");
    }

    /** Reemplazo completo: lo que no viene queda null, whatsappVisibility null queda EVERYONE,
     * y no toca los campos que no son del formulario ni los estilos. */
    @Test
    void updateFromDtoReplacesTheFormFieldsOnly() {
        Users user = user();
        user.setWhatsappVisibility(ContactVisibility.NOBODY);
        user.setBirthDate(LocalDate.of(1995, 4, 12));
        user.getDanceStyles().add(userStyle(user, DanceLevel.BEGINNER));
        UserUpdateDto dto = new UserUpdateDto();
        dto.setShowProfilePublic(false);

        mapper.updateProfileFromDto(dto, user);

        assertThat(user.isShowProfilePublic()).isFalse();
        assertThat(user.getWhatsappVisibility()).isEqualTo(ContactVisibility.EVERYONE);
        assertThat(user.getWhatsapp()).isNull();
        assertThat(user.getInstagram()).isNull();
        assertThat(user.getBirthDate()).isNull();
        assertThat(user.getGender()).isNull();
        assertThat(user.getDanceRole()).isNull();
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getDanceStyles()).hasSize(1);
    }

    private static Users user() {
        Users user = new Users();
        user.setId(OWNER_ID);
        user.setEmail("ana@example.com");
        user.setDisplayName("Ana");
        user.setInstagram("ana.baila");
        user.setWhatsapp(WHATSAPP);
        user.setGender(Gender.FEMALE);
        user.setDanceRole(DanceRole.FOLLOWER);
        return user;
    }

    private static UserDanceStyles userStyle(Users user, DanceLevel level) {
        DanceStyles salsa = new DanceStyles();
        salsa.setId(1L);
        salsa.setName("Salsa");
        salsa.setSlug("salsa");
        UserDanceStyles userStyle = new UserDanceStyles();
        userStyle.getId().setUserId(user.getId());
        userStyle.getId().setDanceStyleId(salsa.getId());
        userStyle.setUser(user);
        userStyle.setDanceStyle(salsa);
        userStyle.setLevel(level);
        return userStyle;
    }
}
