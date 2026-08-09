package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerSummaryDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganizerMapper {

    OrganizerSummaryDto toOrganizerSummary(Organizers organizer);

    OrganizerDetailDto toOrganizerDetail(Organizers organizer);
}
