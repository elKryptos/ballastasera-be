package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.DanceLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Formulario del perfil (PATCH /auth/me): WhatsApp E.164, edad 16-100, maximo 10 estilos. */
class UserUpdateDtoValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsAMinimalForm() {
        assertThat(validator.validate(form())).isEmpty();
    }

    @Test
    void acceptsAFullForm() {
        UserUpdateDto dto = form();
        dto.setInstagram("@juan.baila");
        dto.setWhatsapp("+393331234567");
        dto.setBirthDate(LocalDate.of(1995, 4, 12));
        dto.setDanceStyles(List.of(style(1L, DanceLevel.ADVANCED)));

        assertThat(validator.validate(dto)).isEmpty();
    }

    /** "" = el usuario vacio el input: se acepta y el service lo guarda como null. */
    @Test
    void acceptsAnEmptyWhatsapp() {
        UserUpdateDto dto = form();
        dto.setWhatsapp("");

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void rejectsAWhatsappWithoutCountryCode() {
        UserUpdateDto dto = form();
        dto.setWhatsapp("3331234567");

        assertThat(fields(dto)).containsExactly("whatsapp");
    }

    @Test
    void rejectsAWhatsappWithSpaces() {
        UserUpdateDto dto = form();
        dto.setWhatsapp("+39 333 1234567");

        assertThat(fields(dto)).containsExactly("whatsapp");
    }

    @Test
    void acceptsSomeoneWhoTurns16Today() {
        UserUpdateDto dto = form();
        dto.setBirthDate(LocalDate.now().minusYears(16));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void rejectsSomeoneUnder16() {
        UserUpdateDto dto = form();
        dto.setBirthDate(LocalDate.now().minusYears(16).plusDays(1));

        assertThat(fields(dto)).containsExactly("birthDateValid");
    }

    @Test
    void rejectsSomeoneOver100() {
        UserUpdateDto dto = form();
        dto.setBirthDate(LocalDate.now().minusYears(101));

        assertThat(fields(dto)).containsExactly("birthDateValid");
    }

    @Test
    void rejectsMoreThan10DanceStyles() {
        UserUpdateDto dto = form();
        dto.setDanceStyles(Collections.nCopies(11, style(1L, DanceLevel.BEGINNER)));

        assertThat(fields(dto)).containsExactly("danceStyles");
    }

    @Test
    void rejectsADanceStyleWithoutLevel() {
        UserUpdateDto dto = form();
        dto.setDanceStyles(List.of(style(1L, null)));

        assertThat(fields(dto)).containsExactly("danceStyles[0].level");
    }

    @Test
    void rejectsAFormWithoutShowProfilePublic() {
        UserUpdateDto dto = form();
        dto.setShowProfilePublic(null);

        assertThat(fields(dto)).containsExactly("showProfilePublic");
    }

    private Set<String> fields(UserUpdateDto dto) {
        Set<ConstraintViolation<UserUpdateDto>> violations = validator.validate(dto);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static UserUpdateDto form() {
        UserUpdateDto dto = new UserUpdateDto();
        dto.setShowProfilePublic(true);
        return dto;
    }

    private static UserDanceStyleUpdateDto style(Long danceStyleId, DanceLevel level) {
        UserDanceStyleUpdateDto dto = new UserDanceStyleUpdateDto();
        dto.setDanceStyleId(danceStyleId);
        dto.setLevel(level);
        return dto;
    }
}