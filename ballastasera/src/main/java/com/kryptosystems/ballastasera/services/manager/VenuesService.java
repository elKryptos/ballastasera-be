package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Venues;

import java.util.List;
import java.util.UUID;

public interface VenuesService {
    List<Venues> findAll();
    Venues findById(UUID id);
    List<Venues> findByCityId(Long cityId);
    List<Venues> findByOrganizerId(UUID organizerId);
    Venues save(Venues venue);
    void deleteById(UUID id);
    Venues create(UUID requesterId, VenueCreateDto dto);
    Venues createAsAdmin(UUID adminUserId, VenueCreateDto dto);
    Venues update(UUID id, UUID requesterId, VenueUpdateDto dto);
    void delete(UUID id);
    List<Venues> search(Long cityId, String query);
}
