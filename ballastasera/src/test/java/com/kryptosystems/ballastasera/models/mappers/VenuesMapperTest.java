package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Venues;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class VenuesMapperTest {

    private final VenuesMapper mapper = Mappers.getMapper(VenuesMapper.class);

    @Test
    void updateClearsContactWhenEmpty() {
        Venues venue = new Venues();
        venue.setWhatsapp("+393331234567");
        VenueUpdateDto dto = new VenueUpdateDto();
        dto.setWhatsapp("");

        mapper.updateVenueEntityFromDto(dto, venue);

        assertThat(venue.getWhatsapp()).isNull();
    }

    @Test
    void updateKeepsContactWhenNull() {
        Venues venue = new Venues();
        venue.setWhatsapp("+393331234567");
        VenueUpdateDto dto = new VenueUpdateDto();   // whatsapp no se setea → es null

        mapper.updateVenueEntityFromDto(dto, venue);

        assertThat(venue.getWhatsapp()).isEqualTo("+393331234567");
    }

    @Test
    void createStripsTextAndSavesBlankAsNull() {
        VenueCreateDto dto = new VenueCreateDto();
        dto.setName("  Sala Havana  ");
        dto.setEmail("");
        dto.setInstagram("   ");

        Venues venue = mapper.toVenueEntity(dto);

        assertThat(venue.getName()).isEqualTo("Sala Havana");
        assertThat(venue.getEmail()).isNull();
        assertThat(venue.getInstagram()).isNull();
    }

}