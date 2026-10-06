package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Venues;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface VenuesService {
    List<Venues> findAll();
    Venues findById(UUID id);
    List<Venues> findByCityId(Long cityId);
    List<Venues> findByOrganizerId(UUID organizerId);
    Venues save(Venues venue);
    void deleteById(UUID id);
    /** Admin crea un venue del catalogo. organizerId opcional (perfil propio del lugar). */
    Venues createVenueAsAdmin(UUID adminUserId, VenueCreateDto dto);

    /** Admin edita cualquier venue, sin chequeo de ownership. */
    Venues updateVenueAsAdmin(UUID id, VenueUpdateDto dto);

    /** Admin sube o reemplaza el logo del venue (jpg/png/webp, se guarda como webp). */
    Venues updateLogoAsAdmin(UUID id, MultipartFile file);

    /** Admin quita el logo del venue. */
    Venues deleteLogoAsAdmin(UUID id);

    void deleteVenue(UUID id);
    List<Venues> search(Long cityId, String query);
}
