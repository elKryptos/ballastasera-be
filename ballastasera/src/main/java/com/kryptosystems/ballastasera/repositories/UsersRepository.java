package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
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

    /** Lo mismo que findByIdWithDanceStyles, para /users/by-username/{username}. username ya en minúsculas. */
    @Query("""
            SELECT u FROM Users u
            LEFT JOIN FETCH u.danceStyles uds
            LEFT JOIN FETCH uds.danceStyle
            LEFT JOIN FETCH u.schoolOrganizer
            WHERE u.username = :username
            """)
    Optional<Users> findByUsernameWithDanceStyles(String username);

    /** «Già in uso»: lo tiene otra persona. El propio no cuenta. */
    boolean existsByUsernameAndIdNot(String username, UUID id);

    /** Admin «Utenti bannati»: los mas recientes primero; los baneados antes de la fase 4 (sin banned_at), al final. */
    @Query("""
            SELECT u FROM Users u
            WHERE u.isBanned = true AND u.isDeleted = false
            ORDER BY u.bannedAt DESC NULLS LAST, u.id
            """)
    Page<Users> findBanned(Pageable pageable);

    /**
     * «Scopri»: ids de las sugerencias para viewerId, ya ordenados. Salen de dos sitios: gente que va a sus serate en
     * curso o futuras y muestra sus serate a todos (EVERYONE), y amigos de sus amigos.
     * Fuera: el mismo, a quien ya sigue, bloqueos en cualquier sentido, perfiles privados, borrados, baneados y menores
     * (sin fecha de nacimiento cuenta como adulto, como con el WhatsApp).
     * Orden: comparte serata, marco «Cerco qualcuno» en una de ellas, amigos en comun.
     */
    @Query(value = """
            WITH my_events AS (
                SELECT e.id FROM event_attendance a JOIN events e ON e.id = a.event_id
                WHERE a.user_id = :viewerId AND e.status = 'PUBLISHED'
                AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
            ),
            my_friends AS (
                SELECT f1.followed_id AS id FROM user_follows f1
                JOIN user_follows f2 ON f2.follower_id = f1.followed_id AND f2.followed_id = f1.follower_id
                JOIN users fu ON fu.id = f1.followed_id AND NOT fu.is_deleted AND NOT fu.is_banned
                WHERE f1.follower_id = :viewerId
            ),
            candidates AS (
                SELECT a.user_id AS id FROM event_attendance a
                JOIN users au ON au.id = a.user_id AND au.attendance_visibility = 'EVERYONE'
                WHERE a.event_id IN (SELECT id FROM my_events)
                UNION
                SELECT f1.followed_id FROM user_follows f1
                JOIN user_follows f2 ON f2.follower_id = f1.followed_id AND f2.followed_id = f1.follower_id
                WHERE f1.follower_id IN (SELECT id FROM my_friends)
            )
            SELECT u.id FROM candidates c JOIN users u ON u.id = c.id
            WHERE u.id <> :viewerId
            AND u.show_profile_public AND NOT u.is_deleted AND NOT u.is_banned
            AND (u.birth_date IS NULL OR u.birth_date <= CURRENT_DATE - INTERVAL '18 years')
            AND NOT EXISTS (SELECT 1 FROM user_follows f WHERE f.follower_id = :viewerId AND f.followed_id = u.id)
            AND NOT EXISTS (SELECT 1 FROM user_blocks b
                WHERE (b.blocker_id = :viewerId AND b.blocked_id = u.id)
                   OR (b.blocker_id = u.id AND b.blocked_id = :viewerId))
            ORDER BY
                EXISTS (SELECT 1 FROM event_attendance a WHERE a.user_id = u.id AND u.attendance_visibility = 'EVERYONE'
                        AND a.event_id IN (SELECT id FROM my_events)) DESC,
                EXISTS (SELECT 1 FROM event_attendance a WHERE a.user_id = u.id AND u.attendance_visibility = 'EVERYONE'
                        AND a.looking_for_partner AND a.event_id IN (SELECT id FROM my_events)) DESC,
                (SELECT COUNT(*) FROM my_friends mf
                 JOIN user_follows x ON x.follower_id = mf.id AND x.followed_id = u.id
                 JOIN user_follows y ON y.follower_id = u.id AND y.followed_id = mf.id) DESC,
                u.id
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findSuggestionIds(UUID viewerId, int limit);
}
