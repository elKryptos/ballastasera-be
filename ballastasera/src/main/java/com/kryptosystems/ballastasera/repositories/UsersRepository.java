package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByGoogleId(String googleId);

    /** Usuario con sus estilos y el nombre/slug de cada uno, y su escuela, para /auth/me y el perfil publico. */
    @Query("""
            SELECT u FROM Users u
            LEFT JOIN FETCH u.danceStyles uds
            LEFT JOIN FETCH uds.danceStyle
            LEFT JOIN FETCH u.schoolOrganizer
            WHERE u.id = :id
            """)
    Optional<Users> findByIdWithDanceStyles(UUID id);

    /** Admin «Utenti bannati»: los mas recientes primero; los baneados antes de la fase 4 (sin banned_at), al final. */
    @Query("""
            SELECT u FROM Users u
            WHERE u.isBanned = true AND u.isDeleted = false
            ORDER BY u.bannedAt DESC NULLS LAST, u.id
            """)
    Page<Users> findBanned(Pageable pageable);
}
