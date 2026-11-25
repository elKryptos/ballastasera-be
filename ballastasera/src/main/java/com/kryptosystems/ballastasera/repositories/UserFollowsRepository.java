package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserFollowId;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Listas y contadores sin usuarios borrados ni baneados (mismo filtro que el perfil de GET /users/{id}).
 * Los bloqueados no hace falta filtrarlos: bloquear borra los follows en los dos sentidos y no deja volver a seguir.
 * Listas con keyset (created_at del follow, id del usuario), las mas recientes primero, como /me/attendance?past=true.
 */
public interface UserFollowsRepository extends JpaRepository<UserFollows, UserFollowId> {

    @Query("""
            SELECT COUNT(f) FROM UserFollows f
            WHERE f.followed.id = :userId AND f.follower.isDeleted = false AND f.follower.isBanned = false
            """)
    long countFollowers(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(f) FROM UserFollows f
            WHERE f.follower.id = :userId AND f.followed.isDeleted = false AND f.followed.isBanned = false
            """)
    long countFollowing(@Param("userId") UUID userId);

    /** Quienes siguen a userId. */
    @Query("""
            SELECT f FROM UserFollows f JOIN FETCH f.follower u
            WHERE f.followed.id = :userId AND u.isDeleted = false AND u.isBanned = false
            AND (f.createdAt < :cursorAt OR (f.createdAt = :cursorAt AND u.id < :cursorId))
            ORDER BY f.createdAt DESC, u.id DESC
            """)
    Slice<UserFollows> findFollowers(@Param("userId") UUID userId,
                                     @Param("cursorAt") OffsetDateTime cursorAt,
                                     @Param("cursorId") UUID cursorId,
                                     Pageable pageable);

    /** A quienes sigue userId. */
    @Query("""
            SELECT f FROM UserFollows f JOIN FETCH f.followed u
            WHERE f.follower.id = :userId AND u.isDeleted = false AND u.isBanned = false
            AND (f.createdAt < :cursorAt OR (f.createdAt = :cursorAt AND u.id < :cursorId))
            ORDER BY f.createdAt DESC, u.id DESC
            """)
    Slice<UserFollows> findFollowing(@Param("userId") UUID userId,
                                     @Param("cursorAt") OffsetDateTime cursorAt,
                                     @Param("cursorId") UUID cursorId,
                                     Pageable pageable);

    /**
     * Amigos de userId (se siguen los dos), filtrados por nombre. namePattern ya viene en minusculas y
     * con % y _ escapados con ! (FollowsServiceImpl.toNamePattern). Orden: cuando userId empezo a seguirlos.
     */
    @Query("""
            SELECT f FROM UserFollows f JOIN FETCH f.followed u
            WHERE f.follower.id = :userId AND u.isDeleted = false AND u.isBanned = false
            AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.follower.id = u.id AND f2.followed.id = :userId)
            AND LOWER(u.displayName) LIKE :namePattern ESCAPE '!'
            AND (f.createdAt < :cursorAt OR (f.createdAt = :cursorAt AND u.id < :cursorId))
            ORDER BY f.createdAt DESC, u.id DESC
            """)
    Slice<UserFollows> findFriends(@Param("userId") UUID userId,
                                   @Param("namePattern") String namePattern,
                                   @Param("cursorAt") OffsetDateTime cursorAt,
                                   @Param("cursorId") UUID cursorId,
                                   Pageable pageable);

    /** Total de findFriends con el mismo filtro de nombre. */
    @Query("""
            SELECT COUNT(f) FROM UserFollows f JOIN f.followed u
            WHERE f.follower.id = :userId AND u.isDeleted = false AND u.isBanned = false
            AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.follower.id = u.id AND f2.followed.id = :userId)
            AND LOWER(u.displayName) LIKE :namePattern ESCAPE '!'
            """)
    long countFriends(@Param("userId") UUID userId, @Param("namePattern") String namePattern);

    /** «Avete in comune»: amigos de userA y de userB a la vez (cada uno se sigue con los dos en los dos sentidos). */
    @Query("""
            SELECT u FROM Users u
            WHERE u.isDeleted = false AND u.isBanned = false
            AND EXISTS (SELECT 1 FROM UserFollows a1 WHERE a1.id.followerId = :userA AND a1.id.followedId = u.id)
            AND EXISTS (SELECT 1 FROM UserFollows a2 WHERE a2.id.followerId = u.id AND a2.id.followedId = :userA)
            AND EXISTS (SELECT 1 FROM UserFollows b1 WHERE b1.id.followerId = :userB AND b1.id.followedId = u.id)
            AND EXISTS (SELECT 1 FROM UserFollows b2 WHERE b2.id.followerId = u.id AND b2.id.followedId = :userB)
            ORDER BY u.displayName ASC
            """)
    List<Users> findMutualFriends(@Param("userA") UUID userA, @Param("userB") UUID userB, Limit limit);

    /** Total de findMutualFriends. */
    @Query("""
            SELECT COUNT(u) FROM Users u
            WHERE u.isDeleted = false AND u.isBanned = false
            AND EXISTS (SELECT 1 FROM UserFollows a1 WHERE a1.id.followerId = :userA AND a1.id.followedId = u.id)
            AND EXISTS (SELECT 1 FROM UserFollows a2 WHERE a2.id.followerId = u.id AND a2.id.followedId = :userA)
            AND EXISTS (SELECT 1 FROM UserFollows b1 WHERE b1.id.followerId = :userB AND b1.id.followedId = u.id)
            AND EXISTS (SELECT 1 FROM UserFollows b2 WHERE b2.id.followerId = u.id AND b2.id.followedId = :userB)
            """)
    long countMutualFriends(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** De userIds, a quienes sigue followerId. Para la relacion de cada fila de una lista en una sola query. */
    @Query("SELECT f.id.followedId FROM UserFollows f WHERE f.id.followerId = :followerId AND f.id.followedId IN :userIds")
    Set<UUID> findFollowedIdsAmong(@Param("followerId") UUID followerId, @Param("userIds") Collection<UUID> userIds);

    /** De userIds, quienes siguen a followedId. */
    @Query("SELECT f.id.followerId FROM UserFollows f WHERE f.id.followedId = :followedId AND f.id.followerId IN :userIds")
    Set<UUID> findFollowerIdsAmong(@Param("followedId") UUID followedId, @Param("userIds") Collection<UUID> userIds);

    /**
     * Feed «Novità», «ti segue»: quienes empezaron a seguir a userId desde since, los mas recientes primero.
     * Sin borrados ni baneados (como findFollowers) y sin quien userId silencio.
     */
    @Query("""
            SELECT f FROM UserFollows f JOIN FETCH f.follower u
            WHERE f.followed.id = :userId AND f.createdAt >= :since
            AND u.isDeleted = false AND u.isBanned = false
            AND NOT EXISTS (SELECT 1 FROM UserFollows m WHERE m.id.followerId = :userId AND m.id.followedId = u.id
                            AND m.muted = true)
            ORDER BY f.createdAt DESC
            """)
    List<UserFollows> findRecentFollowers(UUID userId, OffsetDateTime since, Limit limit);

    /**
     * Sugerencias, «N amici in comune»: amistades entre las personas de userIds y los amigos de viewerId.
     * Una fila por cada pareja (persona de userIds = follower, amigo en comun = followed); se cuentan por follower.
     */
    @Query("""
            SELECT f FROM UserFollows f
            WHERE f.id.followerId IN :userIds
            AND f.followed.isDeleted = false AND f.followed.isBanned = false
            AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.id.followerId = f.id.followedId AND f2.id.followedId = f.id.followerId)
            AND EXISTS (SELECT 1 FROM UserFollows v1 WHERE v1.id.followerId = :viewerId AND v1.id.followedId = f.id.followedId)
            AND EXISTS (SELECT 1 FROM UserFollows v2 WHERE v2.id.followerId = f.id.followedId AND v2.id.followedId = :viewerId)
            """)
    List<UserFollows> findFriendshipsWithFriendsOf(UUID viewerId, Collection<UUID> userIds);

    /** Al bloquear: borra el follow en los dos sentidos. */
    @Modifying
    @Query("""
            DELETE FROM UserFollows f
            WHERE (f.id.followerId = :userA AND f.id.followedId = :userB)
               OR (f.id.followerId = :userB AND f.id.followedId = :userA)
            """)
    void deleteBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
