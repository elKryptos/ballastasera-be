package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueMapPinDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.VenuesSummaryDto;
import com.kryptosystems.ballastasera.models.entities.Venues;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** uses BlankStrings: cada String→String se guarda sin espacios en los extremos, y vacío como null. */
@Mapper(componentModel= "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = BlankStrings.class)
public interface VenuesMapper {

    @Mapping(target = "cityName", source = "city.name")
    VenuesSummaryDto toVenueSummaryDto(Venues venues);

    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "organizerName", source = "organizer.name")
    @Mapping(target = "organizerSlug", source = "organizer.slug")
    @Mapping(target = "organizerClaimable", source = "organizer.claimable")
    VenueDetailDto toVenueDetailDto(Venues venues);

    VenueMapPinDto toVenueMapPinDto(Venues venues);

    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    Venues toVenueEntity(VenueCreateDto venueCreateDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "events", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    void updateVenueEntityFromDto(VenueUpdateDto venueUpdateDto, @MappingTarget Venues venues);
}
