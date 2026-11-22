package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.keys.UserFollowId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
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

    /** De userIds, a quienes sigue followerId. Para la relacion de cada fila de una lista en una sola query. */
    @Query("SELECT f.id.followedId FROM UserFollows f WHERE f.id.followerId = :followerId AND f.id.followedId IN :userIds")
    Set<UUID> findFollowedIdsAmong(@Param("followerId") UUID followerId, @Param("userIds") Collection<UUID> userIds);

    /** De userIds, quienes siguen a followedId. */
    @Query("SELECT f.id.followerId FROM UserFollows f WHERE f.id.followedId = :followedId AND f.id.followerId IN :userIds")
    Set<UUID> findFollowerIdsAmong(@Param("followedId") UUID followedId, @Param("userIds") Collection<UUID> userIds);

    /** Al bloquear: borra el follow en los dos sentidos. */
    @Modifying
    @Query("""
            DELETE FROM UserFollows f
            WHERE (f.id.followerId = :userA AND f.id.followedId = :userB)
               OR (f.id.followerId = :userB AND f.id.followedId = :userA)
            """)
    void deleteBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
