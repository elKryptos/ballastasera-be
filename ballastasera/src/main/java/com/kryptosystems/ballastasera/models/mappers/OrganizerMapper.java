package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerSummaryDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrganizerMapper {

    OrganizerSummaryDto toOrganizerSummaryDto(Organizers organizer);

    OrganizerDetailDto toOrganizerDetailDto(Organizers organizer);

    Organizers toOrganizerEntity(OrganizerCreateDto organizerCreateDto);

    void updateOrganizerFromDto(OrganizerUpdateDto organizerUpdateDto, @MappingTarget Organizers organizer);

    /** El dueño (D7): igual que updateOrganizerFromDto pero sin logoUrl. El logo lo cambia con
     * PATCH /rest/organizers/{id}/logo; en texto solo lo pone el admin. */
    @Mapping(target = "logoUrl", ignore = true)
    void updateOwnedOrganizerFromDto(OrganizerUpdateDto organizerUpdateDto, @MappingTarget Organizers organizer);
}
