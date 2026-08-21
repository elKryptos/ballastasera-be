package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerSummaryDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrganizerMapper {

    OrganizerSummaryDto toOrganizerSummary(Organizers organizer);

    OrganizerDetailDto toOrganizerDetail(Organizers organizer);

    Organizers toOrganizerEntity(OrganizerCreateDto organizerCreateDto);

    void updateOrganizerFromDto(OrganizerUpdateDto organizerUpdateDto, @MappingTarget Organizers organizer);
}
