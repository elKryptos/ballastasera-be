package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.enums.OrganizerType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** @ValidOrganizerRequest (reglas por tipo, D6 y D11) más las anotaciones de campo. */
class OrganizerRequestCreateDtoValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsANewPerson() {
        assertThat(validator.validate(newRequest(OrganizerType.PERSON))).isEmpty();
    }

    @Test
    void acceptsANewClubWithAddressAndCity() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.CLUB);
        dto.setVenueAddress("Via Tortona 12");
        dto.setVenueCityId(1L);

        assertThat(validator.validate(dto)).isEmpty();
    }

    /** D6: en CLAIM el perfil y la dirección no cuentan; basta con el organizador pedido. */
    @Test
    void acceptsAClaimWithTargetAndNoProfile() {
        OrganizerRequestCreateDto dto = base(OrganizerRequestType.CLAIM);
        dto.setTargetOrganizerId(UUID.randomUUID());

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void acceptsAnInstagramHandleWithAt() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setInstagram("@salsa.loca_mi");

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void rejectsAClaimWithoutTarget() {
        assertThat(fields(base(OrganizerRequestType.CLAIM))).containsExactly("targetOrganizerId");
    }

    @Test
    void rejectsANewWithoutName() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setName(null);

        assertThat(fields(dto)).containsExactly("name");
    }

    @Test
    void rejectsANewWithoutOrganizerType() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setOrganizerType(null);

        assertThat(fields(dto)).containsExactly("organizerType");
    }

    @Test
    void rejectsANewSchoolWithoutAddress() {
        assertThat(fields(newRequest(OrganizerType.SCHOOL))).containsExactly("venueAddress");
    }

    @Test
    void rejectsANewPersonWithAddress() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setVenueAddress("Via Roma 1");
        dto.setVenueCityId(1L);

        assertThat(fields(dto)).containsExactly("venueAddress");
    }

    @Test
    void rejectsAnAddressWithoutCity() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.VENUE);
        dto.setVenueAddress("Via Roma 1");

        assertThat(fields(dto)).containsExactly("venueAddress");
    }

    @Test
    void rejectsTermsNotAccepted() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setTermsAccepted(false);

        assertThat(fields(dto)).containsExactly("termsAccepted");
    }

    @Test
    void rejectsAnInvalidInstagramHandle() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setInstagram("not valid!");

        assertThat(fields(dto)).containsExactly("instagram");
    }

    @Test
    void rejectsADescriptionLongerThan500() {
        OrganizerRequestCreateDto dto = newRequest(OrganizerType.PERSON);
        dto.setDescription("a".repeat(501));

        assertThat(fields(dto)).containsExactly("description");
    }

    @Test
    void rejectsAMissingType() {
        assertThat(fields(base(null))).containsExactly("type");
    }

    private Set<String> fields(OrganizerRequestCreateDto dto) {
        Set<ConstraintViolation<OrganizerRequestCreateDto>> violations = validator.validate(dto);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static OrganizerRequestCreateDto newRequest(OrganizerType organizerType) {
        OrganizerRequestCreateDto dto = base(OrganizerRequestType.NEW);
        dto.setName("Salsa Loca Milano");
        dto.setOrganizerType(organizerType);
        return dto;
    }

    private static OrganizerRequestCreateDto base(OrganizerRequestType type) {
        OrganizerRequestCreateDto dto = new OrganizerRequestCreateDto();
        dto.setType(type);
        dto.setInstagram("salsa.loca");
        dto.setContactEmail("info@salsaloca.it");
        dto.setTermsAccepted(true);
        return dto;
    }
}
