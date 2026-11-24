package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.NotificationType;
import com.kryptosystems.ballastasera.events.FriendshipCreatedEvent;
import com.kryptosystems.ballastasera.models.dtos.NotificationsActivity;
import com.kryptosystems.ballastasera.models.entities.Notifications;
import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserFollowId;
import com.kryptosystems.ballastasera.repositories.NotificationsRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.services.manager.NotificationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.kryptosystems.ballastasera.utilities.EventTimingUtils.DEFAULT_DURATION;

@Service
@RequiredArgsConstructor
public class NotificationsServiceImpl implements NotificationsService {

    private static final int MAX_LIMIT = 50;
    /** El mismo aviso a la misma pareja, como mucho una vez en este tiempo (seguir, dejar, volver a seguir). */
    private static final Duration REPEAT_WINDOW = Duration.ofDays(7);

    /** Cursor de primera pagina (orden descendente): despues de cualquier aviso posible. */
    private static final OffsetDateTime MAX_CREATED_AT = OffsetDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID MAX_ID = new UUID(-1L, -1L);

    private final NotificationsRepository notificationsRepository;
    private final UserFollowsRepository userFollowsRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Tres o cuatro queries: la pagina, el total y, si hay avisos, a quienes sigue y quienes lo siguen (sin N+1). */
    @Override
    @Transactional(readOnly = true)
    public NotificationsActivity findNotifications(UUID recipientId, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        OffsetDateTime now = OffsetDateTime.now();
        Slice<Notifications> slice = notificationsRepository.findVisible(recipientId, now,
                now.minus(DEFAULT_DURATION), NotificationType.INVITE, EventStatus.PUBLISHED,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId,
                PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT)));

        List<UUID> actorIds = slice.getContent().stream().map(notification -> notification.getActor().getId())
                .distinct().toList();
        Set<UUID> followedIds = actorIds.isEmpty() ? Set.of()
                : userFollowsRepository.findFollowedIdsAmong(recipientId, actorIds);
        Set<UUID> followerIds = actorIds.isEmpty() ? Set.of()
                : userFollowsRepository.findFollowerIdsAmong(recipientId, actorIds);
        return new NotificationsActivity(slice, followedIds, followerIds, countVisible(recipientId, false, now));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID recipientId) {
        return countVisible(recipientId, true, OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void markAllRead(UUID recipientId) {
        notificationsRepository.markAllRead(recipientId, OffsetDateTime.now());
    }

    /**
     * Sin email si follower es privado (el boton lleva a su perfil, que daria 404) o si followed lo silencio: el aviso
     * ya no le sale en la campanita, tampoco tiene que llegarle por email.
     */
    @Override
    @Transactional
    public void notifyFollow(Users follower, Users followed, boolean friendship) {
        NotificationType type = friendship ? NotificationType.NEW_FRIEND : NotificationType.NEW_FOLLOWER;
        if (notificationsRepository.existsByActorIdAndRecipientIdAndTypeAndCreatedAtAfter(follower.getId(),
                followed.getId(), type, OffsetDateTime.now().minus(REPEAT_WINDOW))) {
            return;
        }
        Notifications notification = new Notifications();
        notification.setRecipient(followed);
        notification.setActor(follower);
        notification.setType(type);
        notificationsRepository.save(notification);

        if (friendship && followed.isEmailNotifications() && follower.isShowProfilePublic()
                && !isMuted(followed.getId(), follower.getId())) {
            eventPublisher.publishEvent(new FriendshipCreatedEvent(followed.getEmail(), follower.getId(),
                    follower.getDisplayName()));
        }
    }

    @Override
    @Transactional
    public void deleteUnreadNewFollower(UUID actorId, UUID recipientId) {
        notificationsRepository.deleteUnread(actorId, recipientId, NotificationType.NEW_FOLLOWER);
    }

    @Override
    @Transactional
    public void deleteBetween(UUID userA, UUID userB) {
        notificationsRepository.deleteBetween(userA, userB);
    }

    private long countVisible(UUID recipientId, boolean unreadOnly, OffsetDateTime now) {
        return notificationsRepository.countVisible(recipientId, unreadOnly, now, now.minus(DEFAULT_DURATION),
                NotificationType.INVITE, EventStatus.PUBLISHED);
    }

    /** Como FollowsService.isMuted, por el repository: FollowsServiceImpl ya depende de este service (sin ciclo). */
    private boolean isMuted(UUID followerId, UUID followedId) {
        return userFollowsRepository.findById(UserFollowId.of(followerId, followedId))
                .map(UserFollows::isMuted)
                .orElse(false);
    }
}
