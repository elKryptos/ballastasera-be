package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenuesSummaryDto;
import com.kryptosystems.ballastasera.models.entities.Venues;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel= "spring")
public interface VenuesMapper {

    @Mapping(target = "cityName", source = "city.name")
    VenuesSummaryDto toVenueSummary(Venues venues);

    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "city", ignore = true)
    Venues toVenueEntity(VenueCreateDto venueCreateDto);
}
