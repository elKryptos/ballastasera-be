package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VenueDtoValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsAValidVenueCreateDto() {
        VenueCreateDto dto = new VenueCreateDto();
        dto.setOrganizerId(UUID.randomUUID());
        dto.setCityId(1L);
        dto.setName("Sala Central");
        dto.setAddress("Calle Mayor 1");
        dto.setPostalCode("28001");
        dto.setLatitude(40.4168);
        dto.setLongitude(-3.7038);
        dto.setDescription("Sala para eventos de baile");

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void rejectsVenueCreateDtoWithCoordinatesOutsideTheirGeographicRanges() {
        VenueCreateDto dto = new VenueCreateDto();
        dto.setOrganizerId(UUID.randomUUID());
        dto.setCityId(1L);
        dto.setName("Sala Central");
        dto.setAddress("Calle Mayor 1");
        dto.setLatitude(91.0);
        dto.setLongitude(-181.0);

        assertThat(validator.validate(dto))
                .extracting("propertyPath")
                .extracting(Object::toString)
                .contains("latitude", "longitude");
    }

    @Test
    void rejectsVenueUpdateDtoWithBlankRequiredTextFields() {
        VenueUpdateDto dto = new VenueUpdateDto();
        dto.setName("   ");
        dto.setAddress("");

        assertThat(validator.validate(dto))
                .extracting("propertyPath")
                .extracting(Object::toString)
                .contains("name", "address");
    }

    @Test
    void rejectsVenueUpdateDtoWithInvalidCityAndCoordinates() {
        VenueUpdateDto dto = new VenueUpdateDto();
        dto.setCityId(0L);
        dto.setLatitude(-91.0);
        dto.setLongitude(180.1);

        assertThat(validator.validate(dto))
                .extracting("propertyPath")
                .extracting(Object::toString)
                .contains("cityId", "latitude", "longitude");
    }

    @Test
    void preservesDescriptionInVenueDetailDto() {
        VenueDetailDto dto = new VenueDetailDto();
        dto.setDescription("Sala para eventos de baile");

        assertThat(dto.getDescription()).isEqualTo("Sala para eventos de baile");
    }
}
