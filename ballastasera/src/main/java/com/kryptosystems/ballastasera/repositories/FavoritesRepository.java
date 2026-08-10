package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FavoritesRepository extends JpaRepository<Favorites, UserEventId> {
    List<Favorites> findByUserId(UUID userId);
    List<Favorites> findByEventId(UUID eventId);

    /** Trae los favoritos del usuario con el evento (y organizer/venue/danceStyles)
     * ya cargados, para evitar N+1 al armar las cards en /me/favorites. */
    @Query("""                                                                                                                                                                                                      
          SELECT DISTINCT f FROM Favorites f
          JOIN FETCH f.event e
          JOIN FETCH e.organizer o
          LEFT JOIN FETCH e.venue v
          LEFT JOIN FETCH e.danceStyles ds
          WHERE f.user.id = :userId
          ORDER BY f.createdAt DESC
          """)
    List<Favorites> findByUserIdWithEventDetails(@Param("userId") UUID userId);
}
