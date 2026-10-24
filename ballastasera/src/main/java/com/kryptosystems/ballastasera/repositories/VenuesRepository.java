package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Venues;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenuesRepository extends JpaRepository<Venues, UUID> {
    List<Venues> findByCityId(Long cityId);
    List<Venues> findByOrganizerId(UUID organizerId);
    Optional<Venues> findByCityIdAndNameIgnoreCase(Long cityId, String name);
    List<Venues> findByCityIdAndNameContainingIgnoreCase(Long cityId, String name);
    Optional<Venues> findByCityIdAndNameIgnoreCaseAndIdNot(Long cityId, String name, UUID id);
    /** D11: conecta el venue solo si sigue libre. Devuelve 0 si ya tiene organizador.
     * Es un UPDATE, y por eso lleva @Modifying. Devuelve cuántas filas cambió: 1 si lo enlazó, 0 si el venue ya tenía dueño (409 VENUE_ALREADY_LINKED).
     * flushAutomatically = true → antes del UPDATE guarda en la DB el organizador que approve acaba de crear. Si no, la FK falla.  */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Venues v SET v.organizer = :organizer WHERE v.id = :venueId AND v.organizer IS NULL")
    int linkOrganizerIfFree(UUID venueId, Organizers organizer);
}
