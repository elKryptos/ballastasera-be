package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByGoogleId(String googleId);

    /** Usuario con sus estilos y el nombre/slug de cada uno, para /auth/me y el perfil publico. */
    @Query("""
            SELECT u FROM Users u
            LEFT JOIN FETCH u.danceStyles uds
            LEFT JOIN FETCH uds.danceStyle
            WHERE u.id = :id
            """)
    Optional<Users> findByIdWithDanceStyles(UUID id);
}
