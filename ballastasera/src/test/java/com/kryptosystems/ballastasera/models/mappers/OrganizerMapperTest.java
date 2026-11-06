package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.OrganizerType;
import com.kryptosystems.ballastasera.models.dtos.OrganizerAdminDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizerMapperTest {

    private static final UUID OWNER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private final OrganizerMapper mapper = Mappers.getMapper(OrganizerMapper.class);

    @Test
    void adminDtoCarriesTheOwnerAndClaimed() {
        Users owner = new Users();
        owner.setId(OWNER_ID);
        owner.setDisplayName("Ana");
        owner.setEmail("ana@example.com");
        Organizers organizer = organizer();
        organizer.setUser(owner);
        organizer.setClaimed(true);

        OrganizerAdminDto dto = mapper.toOrganizerAdminDto(organizer);

        assertThat(dto.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(dto.getOwnerName()).isEqualTo("Ana");
        assertThat(dto.getOwnerEmail()).isEqualTo("ana@example.com");
        assertThat(dto.isClaimed()).isTrue();
        assertThat(dto.getType()).isEqualTo("SCHOOL");
    }

    @Test
    void adminDtoWithoutOwnerLeavesTheOwnerFieldsNull() {
        OrganizerAdminDto dto = mapper.toOrganizerAdminDto(organizer());

        assertThat(dto.getOwnerId()).isNull();
        assertThat(dto.getOwnerName()).isNull();
        assertThat(dto.getOwnerEmail()).isNull();
        assertThat(dto.isClaimed()).isFalse();
    }

    private Organizers organizer() {
        Organizers organizer = new Organizers();
        organizer.setName("Salsa Loca Milano");
        organizer.setType(OrganizerType.SCHOOL);
        return organizer;
    }
}
