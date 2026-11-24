package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.InvitePhrase;
import com.kryptosystems.ballastasera.enums.NotificationType;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.events.InviteSentEvent;
import com.kryptosystems.ballastasera.exceptions.InviteLimitException;
import com.kryptosystems.ballastasera.exceptions.SocialActionException;
import com.kryptosystems.ballastasera.models.dtos.FollowsActivity;
import com.kryptosystems.ballastasera.models.dtos.InviteFriendsActivity;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.NotificationsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.FollowsService;
import com.kryptosystems.ballastasera.services.manager.InvitesService;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitesServiceImpl implements InvitesService {

    private static final int MAX_INVITES = 10;
    /** El limite cuenta las invitaciones de las ultimas 24 horas (ventana movil, no el dia del calendario). */
    private static final Duration INVITES_WINDOW = Duration.ofHours(24);

    private final UsersRepository usersRepository;
    private final EventsRepository eventsRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final NotificationsRepository notificationsRepository;
    private final FollowsService followsService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Primero lo que vale para cualquier invitado (quien invita y la serata), despues el invitado. El email no sale si
     * el invitado silencio a quien invita: el aviso tampoco le aparece en la campanita.
     */
    @Override
    @Transactional
    public void invite(UUID inviterId, UUID inviteeId, UUID eventId, InvitePhrase phrase) {
        if (inviterId.equals(inviteeId)) {
            throw new SocialActionException(SocialActionException.SELF_ACTION, "You cannot invite yourself");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Users inviter = findInviter(inviterId);
        Events event = findInvitableEvent(inviterId, eventId, now);
        Users invitee = findInvitableFriend(inviterId, inviteeId, eventId);
        if (notificationsRepository.existsByActorIdAndRecipientIdAndEventIdAndType(inviterId, inviteeId, eventId,
                NotificationType.INVITE)) {
            return;
        }
        checkLimit(inviterId, now);

        int inserted = notificationsRepository.insertInvite(inviterId, inviteeId, eventId, phrase.name(), now);
        if (inserted == 1 && invitee.isEmailNotifications() && !followsService.isMuted(inviteeId, inviterId)) {
            eventPublisher.publishEvent(new InviteSentEvent(invitee.getEmail(), inviter.getDisplayName(), eventId,
                    event.getTitle(), event.getStartAt(), phrase));
        }
    }

    /** Mismos 400 que invite para quien invita y la serata, asi el dialogo no muestra amigos que no se pueden invitar.
     * Despues, las de /me/friends y tres queries para toda la pagina: invitados, quienes ya van y el limite. */
    @Override
    @Transactional(readOnly = true)
    public InviteFriendsActivity findInviteFriends(UUID inviterId, UUID eventId, String q, OffsetDateTime cursorAt,
                                                   UUID cursorId, int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        findInviter(inviterId);
        findInvitableEvent(inviterId, eventId, now);

        FollowsActivity friends = followsService.findFriends(inviterId, q, cursorAt, cursorId, limit);
        Set<UUID> ids = friends.friendIds();
        Set<UUID> invitedIds = ids.isEmpty() ? Set.of()
                : notificationsRepository.findInvitedIdsAmong(inviterId, eventId, NotificationType.INVITE, ids);
        Set<UUID> goingIds = ids.isEmpty() ? Set.of()
                : eventAttendanceRepository.findVisibleGoingIdsAmong(eventId, ids, ContactVisibility.NOBODY);
        long sent = notificationsRepository.countByActorIdAndTypeAndCreatedAtAfter(inviterId, NotificationType.INVITE,
                now.minus(INVITES_WINDOW));
        return new InviteFriendsActivity(friends, invitedIds, goingIds, (int) Math.max(0, MAX_INVITES - sent));
    }

    /** Un perfil privado queda aislado: no invita (PROFILE_PRIVATE). */
    private Users findInviter(UUID inviterId) {
        Users inviter = usersRepository.findById(inviterId)
                .orElseThrow(() -> userNotFound(inviterId));
        if (!inviter.isShowProfilePublic()) {
            throw new SocialActionException(SocialActionException.PROFILE_PRIVATE, "A private profile cannot invite");
        }
        return inviter;
    }

    /** Publicada, no terminada (en curso tambien vale) y quien invita dijo «Ci vado». */
    private Events findInvitableEvent(UUID inviterId, UUID eventId, OffsetDateTime now) {
        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id " + eventId));
        if (event.getStatus() != EventStatus.PUBLISHED || EventTimingUtils.hasEnded(event, now)) {
            throw new SocialActionException(SocialActionException.EVENT_NOT_INVITABLE,
                    "Event " + eventId + " is not published or already ended");
        }
        if (!eventAttendanceRepository.existsById(UserEventId.of(inviterId, eventId))) {
            throw new SocialActionException(SocialActionException.NOT_GOING,
                    "You can only invite to an event you are going to");
        }
        return event;
    }

    /**
     * Amigo, publico y que no va ya. Con un bloqueo no hay follows: cae en NOT_FRIENDS, sin revelar el bloqueo. Si
     * oculta sus serate a todos, se lo deja invitar aunque vaya: rechazarlo revelaria que va.
     */
    private Users findInvitableFriend(UUID inviterId, UUID inviteeId, UUID eventId) {
        Users invitee = usersRepository.findById(inviteeId)
                .filter(user -> !user.isDeleted() && !user.isBanned())
                .orElseThrow(() -> userNotFound(inviteeId));
        if (followsService.findRelation(inviterId, inviteeId) != SocialRelation.MUTUAL) {
            throw new SocialActionException(SocialActionException.NOT_FRIENDS, "You can only invite friends");
        }
        if (!invitee.isShowProfilePublic()) {
            throw new SocialActionException(SocialActionException.FRIEND_PRIVATE, "A private profile cannot be invited");
        }
        if (invitee.getAttendanceVisibility() != ContactVisibility.NOBODY
                && eventAttendanceRepository.existsById(UserEventId.of(inviteeId, eventId))) {
            throw new SocialActionException(SocialActionException.ALREADY_GOING, "Your friend is already going");
        }
        return invitee;
    }

    /** 429 con Retry-After: cuando la invitacion mas vieja de la ventana cumple 24 horas se libera un hueco. */
    private void checkLimit(UUID inviterId, OffsetDateTime now) {
        OffsetDateTime since = now.minus(INVITES_WINDOW);
        if (notificationsRepository.countByActorIdAndTypeAndCreatedAtAfter(inviterId, NotificationType.INVITE, since)
                < MAX_INVITES) {
            return;
        }
        OffsetDateTime oldest = notificationsRepository.findOldestInviteSince(inviterId, NotificationType.INVITE, since);
        long retryAfter = oldest == null ? 1 : Duration.between(now, oldest.plus(INVITES_WINDOW)).toSeconds();
        throw new InviteLimitException(Math.max(1, retryAfter));
    }

    private static EntityNotFoundException userNotFound(UUID userId) {
        return new EntityNotFoundException("User not found with id " + userId);
    }
}
