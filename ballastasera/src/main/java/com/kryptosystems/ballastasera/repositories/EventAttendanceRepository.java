package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, UserEventId> {
    List<EventAttendance> findByUserId(UUID userId);
    List<EventAttendance> findByEventId(UUID eventId);

    @Query("SELECT a.id.eventId FROM EventAttendance a WHERE a.user.id = :userId")
    List<UUID> findEventsIdsByUserId( @Param("userId") UUID userId);

    /**
     * Solo quienes van, activaron show_profile_public y no estan borrados ni baneados
     * (mismo filtro que el perfil de GET /users/{id}, si no la lista enlaza a perfiles que dan 404).
     * El conteo total de "van" (arriba) es independiente y siempre incluye a todos.
     * Sin quien tenga un bloqueo con viewerId en cualquier sentido. Sin login, viewerId es
     * EventAttendanceServiceImpl.ANONYMOUS_VIEWER, que no tiene bloqueos ni amigos.
     * attendanceVisibility: everyone (EVERYONE) sale siempre, mutuals (MUTUALS) solo si viewerId es amigo
     * (se siguen los dos), NOBODY nunca. Cada uno se ve siempre a si mismo.
     */
    @Query(value = """
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.user u
            WHERE ea.event.id = :eventId AND u.showProfilePublic = true
            AND u.isDeleted = false AND u.isBanned = false
            AND NOT EXISTS (SELECT 1 FROM UserBlocks b
                WHERE (b.id.blockerId = :viewerId AND b.id.blockedId = u.id)
                   OR (b.id.blockerId = u.id AND b.id.blockedId = :viewerId))
            AND (u.attendanceVisibility = :everyone
                 OR u.id = :viewerId
                 OR (u.attendanceVisibility = :mutuals
                     AND EXISTS (SELECT 1 FROM UserFollows f1 WHERE f1.id.followerId = :viewerId AND f1.id.followedId = u.id)
                     AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.id.followerId = u.id AND f2.id.followedId = :viewerId)))
            ORDER BY ea.createdAt ASC
            """,
            countQuery = """
            SELECT COUNT(ea) FROM EventAttendance ea JOIN ea.user u
            WHERE ea.event.id = :eventId AND u.showProfilePublic = true
            AND u.isDeleted = false AND u.isBanned = false
            AND NOT EXISTS (SELECT 1 FROM UserBlocks b
                WHERE (b.id.blockerId = :viewerId AND b.id.blockedId = u.id)
                   OR (b.id.blockerId = u.id AND b.id.blockedId = :viewerId))
            AND (u.attendanceVisibility = :everyone
                 OR u.id = :viewerId
                 OR (u.attendanceVisibility = :mutuals
                     AND EXISTS (SELECT 1 FROM UserFollows f1 WHERE f1.id.followerId = :viewerId AND f1.id.followedId = u.id)
                     AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.id.followerId = u.id AND f2.id.followedId = :viewerId)))
            """)
    Page<EventAttendance> findPublicByEventId(@Param("eventId") UUID eventId,
                                              @Param("viewerId") UUID viewerId,
                                              @Param("everyone") ContactVisibility everyone,
                                              @Param("mutuals") ContactVisibility mutuals,
                                              Pageable pageable);

    /** Ids de los eventos a los que el usuario marco "voy", para /me/attendance:
     * solo publicados y en curso o futuros, los mas cercanos primero.
     * Mismo keyset (start_at, id) y mismo patron ids + Slice que FavoritesRepository. */
    @Query(value = """
            SELECT e.id FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
            AND (e.start_at, e.id) > (:cursorStartAt, :cursorId)
            ORDER BY e.start_at ASC, e.id ASC
            """, nativeQuery = true)
    Slice<UUID> findUpcomingEventsIdsByUserId(@Param("userId") UUID userId,
                                              @Param("cursorStartAt") OffsetDateTime cursorStartAt,
                                              @Param("cursorId") UUID cursorId,
                                              Pageable pageable);

    /** Total para el contador de /me/attendance, con el mismo filtro que la lista. */
    @Query(value = """
            SELECT COUNT(*) FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
            """, nativeQuery = true)
    long countUpcomingByUserId(@Param("userId") UUID userId);

    /** Ids de eventos con "voy" ya terminados, para /me/attendance?past=true:
     * del mas reciente al mas antiguo. Keyset invertido igual que en FavoritesRepository. */
    @Query(value = """
            SELECT e.id FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            AND (e.start_at , e.id) < (:cursorStartAt, :cursorId)
            ORDER BY e.start_at DESC, e.id DESC
            """, nativeQuery = true)
    Slice<UUID> findPastEventsIdsByUserId(@Param("userId") UUID userId,
                                          @Param("cursorStartAt") OffsetDateTime cursorStartAt,
                                          @Param("cursorId") UUID cursorId,
                                          Pageable pageable);

    /** Total para el contador de serate passate en /me/attendance. */
    @Query(value = """
            SELECT COUNT(*) FROM event_attendance a JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userId AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            """, nativeQuery = true)
    long countPastByUserId(@Param("userId") UUID userId);

    /** «Avete in comune»: serate ya terminadas a las que dijeron «Ci vado» los dos. Mismo filtro que countPastByUserId. */
    @Query(value = """
            SELECT COUNT(*) FROM event_attendance a
            JOIN event_attendance b ON b.event_id = a.event_id
            JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userA AND b.user_id = :userB AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            """, nativeQuery = true)
    long countPastTogether(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** La ultima de esas serate juntos. */
    @Query(value = """
            SELECT e.id FROM event_attendance a
            JOIN event_attendance b ON b.event_id = a.event_id
            JOIN events e ON e.id = a.event_id
            WHERE a.user_id = :userA AND b.user_id = :userB AND e.status = 'PUBLISHED'
            AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') <= now()
            ORDER BY e.start_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findLastPastEventIdTogether(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** Pallino del perfil: marco «Cerco qualcuno» en una serata en curso o que empieza en las proximas 24 horas. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM event_attendance a JOIN events e ON e.id = a.event_id
                WHERE a.user_id = :userId AND a.looking_for_partner = TRUE AND e.status = 'PUBLISHED'
                AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
                AND e.start_at < now() + INTERVAL '24 hours')
            """, nativeQuery = true)
    boolean existsLookingForPartnerSoon(@Param("userId") UUID userId);

    /**
     * «Sara e Luca ci vanno»: amigos de viewerId (se siguen los dos) que van a la serata. Mismo filtro que
     * «Chi ci va»: perfil publico, ni borrados ni baneados, y sin quien eligio que nadie vea sus serate (nobody).
     * Los bloqueados no hace falta filtrarlos: con un bloqueo no hay follows.
     */
    @Query("""
            SELECT u FROM EventAttendance ea JOIN ea.user u
            WHERE ea.event.id = :eventId AND u.showProfilePublic = true
            AND u.isDeleted = false AND u.isBanned = false
            AND u.attendanceVisibility <> :nobody
            AND EXISTS (SELECT 1 FROM UserFollows f1 WHERE f1.id.followerId = :viewerId AND f1.id.followedId = u.id)
            AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.id.followerId = u.id AND f2.id.followedId = :viewerId)
            ORDER BY ea.createdAt ASC
            """)
    List<Users> findFriendsGoing(@Param("eventId") UUID eventId,
                                 @Param("viewerId") UUID viewerId,
                                 @Param("nobody") ContactVisibility nobody);

    /** Primeros asistentes publicos con foto de cada evento, para las caritas
     * del mapa y la lista. Mismo filtro de privacidad que findByEventIdAndStatus...
     * Solo attendanceVisibility = everyone (EVERYONE): la lista es la misma para todos, no depende de quien mira. */
    @Query(value = """
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.user u
            WHERE ea.id.eventId IN :eventIds
            AND u.showProfilePublic = true AND u.avatarUrl IS NOT NULL
            AND u.isDeleted = false AND u.isBanned = false
            AND u.attendanceVisibility = :everyone
            AND (SELECT COUNT(ea2) FROM EventAttendance ea2 JOIN ea2.user u2
               WHERE ea2.id.eventId = ea.id.eventId
               AND u2.showProfilePublic = true AND u2.avatarUrl IS NOT NULL
               AND u2.isDeleted = false AND u2.isBanned = false
               AND u2.attendanceVisibility = :everyone
               AND ea2.createdAt < ea.createdAt) < :perEvent
          ORDER BY ea.createdAt ASC
          """)
    List<EventAttendance> findAvatarPreviewsByEventIdIn(@Param("eventIds") Collection<UUID> eventIds,
                                                        @Param("perEvent") long perEvent,
                                                        @Param("everyone") ContactVisibility everyone);

    /**
     * Feed «Stasera escono»: «Ci vado» de la gente que viewerId sigue sin silenciar, a serate en curso o que empiezan
     * antes de until. Visibilidad como «Chi ci va»: everyone siempre, mutuals solo si tambien siguen a viewerId.
     * Sin end_at, en curso = empezo despues de liveSince (now - 4 horas, la duracion por defecto).
     * Los bloqueados no hace falta filtrarlos: con un bloqueo no hay follows. El venue para «Città Studi · dalle 22:00».
     */
    @Query("""
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.user u JOIN FETCH ea.event e LEFT JOIN FETCH e.venue v
            WHERE e.status = :published
            AND (e.endAt > :now OR (e.endAt IS NULL AND e.startAt > :liveSince))
            AND e.startAt < :until
            AND EXISTS (SELECT 1 FROM UserFollows f WHERE f.id.followerId = :viewerId AND f.id.followedId = u.id
                        AND f.muted = false)
            AND u.showProfilePublic = true AND u.isDeleted = false AND u.isBanned = false
            AND (u.attendanceVisibility = :everyone
                 OR (u.attendanceVisibility = :mutuals
                     AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.id.followerId = u.id AND f2.id.followedId = :viewerId)))
            ORDER BY e.startAt ASC, e.id ASC, ea.createdAt ASC
            """)
    List<EventAttendance> findFollowedGoingTonight(UUID viewerId, OffsetDateTime now, OffsetDateTime liveSince,
                                                   OffsetDateTime until, EventStatus published,
                                                   ContactVisibility everyone, ContactVisibility mutuals);

    /**
     * Feed «Novità», «va a X»: «Ci vado» dados desde since por la gente que viewerId sigue, a serate que empiezan
     * entre from y until (las de esta noche ya salen en «Stasera escono»). Mismo filtro que findFollowedGoingTonight.
     * Los mas recientes primero.
     */
    @Query("""
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.user u JOIN FETCH ea.event e
            WHERE e.status = :published
            AND e.startAt >= :from AND e.startAt < :until
            AND ea.createdAt >= :since
            AND EXISTS (SELECT 1 FROM UserFollows f WHERE f.id.followerId = :viewerId AND f.id.followedId = u.id
                        AND f.muted = false)
            AND u.showProfilePublic = true AND u.isDeleted = false AND u.isBanned = false
            AND (u.attendanceVisibility = :everyone
                 OR (u.attendanceVisibility = :mutuals
                     AND EXISTS (SELECT 1 FROM UserFollows f2 WHERE f2.id.followerId = u.id AND f2.id.followedId = :viewerId)))
            ORDER BY ea.createdAt DESC
            """)
    List<EventAttendance> findFollowedGoingSoon(UUID viewerId, OffsetDateTime since, OffsetDateTime from,
                                                OffsetDateTime until, EventStatus published,
                                                ContactVisibility everyone, ContactVisibility mutuals, Limit limit);

    /** De eventIds, a cuales dijo «Ci vado» userId: el «ci vai anche tu» del feed en una sola query. */
    @Query("SELECT a.id.eventId FROM EventAttendance a WHERE a.id.userId = :userId AND a.id.eventId IN :eventIds")
    Set<UUID> findEventIdsAmong(UUID userId, Collection<UUID> eventIds);

    /**
     * Dialogo «Invita a ballare» («Ci va già»): de userIds, quienes dijeron «Ci vado» a la serata. Sin quien eligio
     * que nadie vea sus serate (nobody): a esos se los deja invitar, para no revelar que van.
     */
    @Query("""
            SELECT a.id.userId FROM EventAttendance a
            WHERE a.id.eventId = :eventId AND a.id.userId IN :userIds AND a.user.attendanceVisibility <> :nobody
            """)
    Set<UUID> findVisibleGoingIdsAmong(@Param("eventId") UUID eventId,
                                       @Param("userIds") Collection<UUID> userIds,
                                       @Param("nobody") ContactVisibility nobody);

    /**
     * Sugerencias: «Ci vado» de userIds a serate en curso o futuras a las que tambien va viewerId, las mas cercanas
     * primero. Solo de quien muestra sus serate a todos (everyone): son desconocidos para viewerId.
     */
    @Query("""
            SELECT ea FROM EventAttendance ea JOIN FETCH ea.event e JOIN ea.user u
            WHERE ea.id.userId IN :userIds AND e.status = :published
            AND (e.endAt > :now OR (e.endAt IS NULL AND e.startAt > :liveSince))
            AND u.attendanceVisibility = :everyone
            AND EXISTS (SELECT 1 FROM EventAttendance mine WHERE mine.id.userId = :viewerId AND mine.id.eventId = e.id)
            ORDER BY e.startAt ASC
            """)
    List<EventAttendance> findSharedUpcoming(UUID viewerId, Collection<UUID> userIds, OffsetDateTime now,
                                             OffsetDateTime liveSince, EventStatus published,
                                             ContactVisibility everyone);
}
