package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.NotificationType;
import com.kryptosystems.ballastasera.models.entities.Notifications;
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
 * Campanita. Lo que se ve se filtra al leer, no se borra: sin avisos de baneados ni de borrados, sin los de quien
 * recipient silencio (al reactivarlo vuelven) y sin invitaciones a serate que no estan publicadas o ya terminaron
 * (sin end_at, terminada = empezo antes de liveSince, now - 4 horas). Lista y contadores usan el mismo filtro.
 */
public interface NotificationsRepository extends JpaRepository<Notifications, UUID> {

    /** Avisos visibles de recipientId, los mas recientes primero, con keyset (created_at, id). */
    @Query("""
            SELECT n FROM Notifications n JOIN FETCH n.actor a LEFT JOIN FETCH n.event e
            WHERE n.recipient.id = :recipientId
            AND a.isDeleted = false AND a.isBanned = false
            AND NOT EXISTS (SELECT 1 FROM UserFollows m WHERE m.id.followerId = :recipientId AND m.id.followedId = a.id
                            AND m.muted = true)
            AND (n.type <> :invite
                 OR (e.status = :published AND (e.endAt > :now OR (e.endAt IS NULL AND e.startAt > :liveSince))))
            AND (n.createdAt < :cursorAt OR (n.createdAt = :cursorAt AND n.id < :cursorId))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Slice<Notifications> findVisible(@Param("recipientId") UUID recipientId,
                                     @Param("now") OffsetDateTime now,
                                     @Param("liveSince") OffsetDateTime liveSince,
                                     @Param("invite") NotificationType invite,
                                     @Param("published") EventStatus published,
                                     @Param("cursorAt") OffsetDateTime cursorAt,
                                     @Param("cursorId") UUID cursorId,
                                     Pageable pageable);

    /** Total de findVisible (unreadOnly = false) o los sin leer de la campanita (unreadOnly = true). */
    @Query("""
            SELECT COUNT(n) FROM Notifications n JOIN n.actor a LEFT JOIN n.event e
            WHERE n.recipient.id = :recipientId
            AND (:unreadOnly = false OR n.readAt IS NULL)
            AND a.isDeleted = false AND a.isBanned = false
            AND NOT EXISTS (SELECT 1 FROM UserFollows m WHERE m.id.followerId = :recipientId AND m.id.followedId = a.id
                            AND m.muted = true)
            AND (n.type <> :invite
                 OR (e.status = :published AND (e.endAt > :now OR (e.endAt IS NULL AND e.startAt > :liveSince))))
            """)
    long countVisible(@Param("recipientId") UUID recipientId,
                      @Param("unreadOnly") boolean unreadOnly,
                      @Param("now") OffsetDateTime now,
                      @Param("liveSince") OffsetDateTime liveSince,
                      @Param("invite") NotificationType invite,
                      @Param("published") EventStatus published);

    /** Al abrir la campanita: todos leidos, tambien los que no se ven (silenciados), asi no vuelven como nuevos. */
    @Modifying
    @Query("UPDATE Notifications n SET n.readAt = :now WHERE n.recipient.id = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") OffsetDateTime now);

    /** Sin repetidos: ¿actorId ya le genero a recipientId un aviso de este tipo desde since? */
    boolean existsByActorIdAndRecipientIdAndTypeAndCreatedAtAfter(UUID actorId, UUID recipientId,
                                                                  NotificationType type, OffsetDateTime since);

    /** Los avisos de este tipo, sin leer, que actorId le genero a recipientId. */
    @Modifying
    @Query("""
            DELETE FROM Notifications n
            WHERE n.actor.id = :actorId AND n.recipient.id = :recipientId AND n.type = :type AND n.readAt IS NULL
            """)
    int deleteUnread(@Param("actorId") UUID actorId, @Param("recipientId") UUID recipientId,
                     @Param("type") NotificationType type);

    /** Al bloquear: todos los avisos entre los dos, en los dos sentidos, invitaciones incluidas. */
    @Modifying
    @Query("""
            DELETE FROM Notifications n
            WHERE (n.actor.id = :userA AND n.recipient.id = :userB)
               OR (n.actor.id = :userB AND n.recipient.id = :userA)
            """)
    int deleteBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /**
     * Invitacion, 1 por persona y serata: si ya existe no hace nada (indice uq_notifications_invite) y devuelve 0.
     * Nativa por el ON CONFLICT: dos clicks a la vez no dan un 500 por la clave duplicada. createdAt viene de la app
     * (no now() de Postgres), como el @CreationTimestamp de los otros avisos: una sola hora para ordenar y contar.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (recipient_id, actor_id, type, event_id, invite_phrase, created_at)
            VALUES (:recipientId, :actorId, 'INVITE', :eventId, CAST(:phrase AS invite_phrase), :createdAt)
            ON CONFLICT (actor_id, recipient_id, event_id) WHERE type = 'INVITE' DO NOTHING
            """, nativeQuery = true)
    int insertInvite(@Param("actorId") UUID actorId, @Param("recipientId") UUID recipientId,
                     @Param("eventId") UUID eventId, @Param("phrase") String phrase,
                     @Param("createdAt") OffsetDateTime createdAt);

    /** ¿actorId ya invito a recipientId a la serata? Volver a invitar no hace nada ni cuenta para el limite. */
    boolean existsByActorIdAndRecipientIdAndEventIdAndType(UUID actorId, UUID recipientId, UUID eventId,
                                                           NotificationType type);

    /** Limite de invitaciones: cuantas mando actorId desde since. */
    long countByActorIdAndTypeAndCreatedAtAfter(UUID actorId, NotificationType type, OffsetDateTime since);

    /** La invitacion mas vieja de actorId desde since: cuando sale de la ventana se libera un hueco (Retry-After). */
    @Query("SELECT MIN(n.createdAt) FROM Notifications n WHERE n.actor.id = :actorId AND n.type = :invite AND n.createdAt > :since")
    OffsetDateTime findOldestInviteSince(@Param("actorId") UUID actorId,
                                         @Param("invite") NotificationType invite,
                                         @Param("since") OffsetDateTime since);

    /** Dialogo «Invita a ballare»: de userIds, a quienes ya invito actorId a la serata. Una query por pagina. */
    @Query("""
            SELECT n.recipient.id FROM Notifications n
            WHERE n.actor.id = :actorId AND n.event.id = :eventId AND n.type = :invite AND n.recipient.id IN :userIds
            """)
    Set<UUID> findInvitedIdsAmong(@Param("actorId") UUID actorId,
                                  @Param("eventId") UUID eventId,
                                  @Param("invite") NotificationType invite,
                                  @Param("userIds") Collection<UUID> userIds);

    /** Limpieza: los avisos se borran a los 60 dias (NotificationsCleanupJob). */
    @Modifying
    @Query("DELETE FROM Notifications n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
