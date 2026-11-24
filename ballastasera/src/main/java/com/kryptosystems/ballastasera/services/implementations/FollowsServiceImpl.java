package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.exceptions.SocialActionException;
import com.kryptosystems.ballastasera.models.dtos.FollowsActivity;
import com.kryptosystems.ballastasera.models.dtos.SocialCounts;
import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserFollowId;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.UserBlocksRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.FollowsService;
import com.kryptosystems.ballastasera.services.manager.NotificationsService;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class FollowsServiceImpl implements FollowsService {

    private static final int MAX_LIMIT = 50;

    /** Cursor de primera pagina (orden descendente): despues de cualquier follow posible. */
    private static final OffsetDateTime MAX_CREATED_AT = OffsetDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID MAX_ID = new UUID(-1L, -1L);

    private final UserFollowsRepository userFollowsRepository;
    private final UserBlocksRepository userBlocksRepository;
    private final UsersRepository usersRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final NotificationsService notificationsService;

    @Override
    @Transactional
    public void follow(UUID followerId, UUID followedId) {
        if (followerId.equals(followedId)) {
            throw new SocialActionException(SocialActionException.SELF_ACTION, "You cannot follow yourself");
        }
        // Mismo 404 en todos los casos: no se revela si el perfil es privado o si hay un bloqueo
        Users followed = usersRepository.findById(followedId)
                .filter(user -> user.isShowProfilePublic() && !user.isDeleted() && !user.isBanned())
                .orElseThrow(() -> userNotFound(followedId));
        if (userBlocksRepository.existsBetween(followerId, followedId)) {
            throw userNotFound(followedId);
        }

        UserFollowId id = UserFollowId.of(followerId, followedId);
        if (userFollowsRepository.existsById(id)) {
            return;
        }
        Users follower = usersRepository.getReferenceById(followerId);
        UserFollows follow = new UserFollows();
        follow.setId(id);
        follow.setFollower(follower);
        follow.setFollowed(followed);
        userFollowsRepository.save(follow);

        // Si followed ya lo seguia, ahora son amigos: «ora siete amici» en vez de «ti segue»
        boolean friendship = userFollowsRepository.existsById(UserFollowId.of(followedId, followerId));
        notificationsService.notifyFollow(follower, followed, friendship);
    }

    /** Se borra tambien el «ti segue» sin leer: seguir y dejar de seguir no llena la campanita. */
    @Override
    @Transactional
    public void unfollow(UUID followerId, UUID followedId) {
        userFollowsRepository.deleteById(UserFollowId.of(followerId, followedId));
        notificationsService.deleteUnreadNewFollower(followerId, followedId);
    }

    /** Sin save(): la entidad esta gestionada y el dirty checking hace el UPDATE en el commit. */
    @Override
    @Transactional
    public void setMuted(UUID followerId, UUID followedId, boolean muted) {
        UserFollows follow = userFollowsRepository.findById(UserFollowId.of(followerId, followedId))
                .orElseThrow(() -> new EntityNotFoundException("You do not follow user " + followedId));
        follow.setMuted(muted);
    }

    @Override
    public boolean isMuted(UUID followerId, UUID followedId) {
        return userFollowsRepository.findById(UserFollowId.of(followerId, followedId))
                .map(UserFollows::isMuted)
                .orElse(false);
    }

    @Override
    public SocialRelation findRelation(UUID viewerId, UUID userId) {
        if (viewerId.equals(userId)) {
            return SocialRelation.NONE;
        }
        boolean viewerFollows = userFollowsRepository.existsById(UserFollowId.of(viewerId, userId));
        boolean followsViewer = userFollowsRepository.existsById(UserFollowId.of(userId, viewerId));
        return SocialRelation.of(viewerFollows, followsViewer);
    }

    /** Con los traguardi: serate pasadas, locales distintos y cuando empezo la primera. */
    @Override
    public SocialCounts countSocial(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SocialCounts(
                userFollowsRepository.countFollowers(userId),
                userFollowsRepository.countFollowing(userId),
                eventAttendanceRepository.countPastByUserId(userId),
                eventAttendanceRepository.countPastVenuesByUserId(userId),
                eventAttendanceRepository.findFirstPastStartAt(userId, EventStatus.PUBLISHED, now,
                        now.minus(EventTimingUtils.DEFAULT_DURATION)));
    }

    /** Todos siguen a userId: son amigos los que userId sigue tambien. */
    @Override
    @Transactional(readOnly = true)
    public FollowsActivity findFollowers(UUID userId, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        Slice<UserFollows> slice = userFollowsRepository.findFollowers(userId,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId, toPageable(limit));

        // Una sola query para toda la pagina (sin N+1)
        List<UUID> ids = idsOf(slice, UserFollows::getFollower);
        Set<UUID> friendIds = ids.isEmpty() ? Set.of() : userFollowsRepository.findFollowedIdsAmong(userId, ids);
        return new FollowsActivity(slice, friendIds, userFollowsRepository.countFollowers(userId));
    }

    /** userId sigue a todos: son amigos los que lo siguen tambien. */
    @Override
    @Transactional(readOnly = true)
    public FollowsActivity findFollowing(UUID userId, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        Slice<UserFollows> slice = userFollowsRepository.findFollowing(userId,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId, toPageable(limit));

        List<UUID> ids = idsOf(slice, UserFollows::getFollowed);
        Set<UUID> friendIds = ids.isEmpty() ? Set.of() : userFollowsRepository.findFollowerIdsAmong(userId, ids);
        return new FollowsActivity(slice, friendIds, userFollowsRepository.countFollowing(userId));
    }

    /** Todos son amigos: friendIds son todos los de la pagina, sin query. */
    @Override
    @Transactional(readOnly = true)
    public FollowsActivity findFriends(UUID userId, String q, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        String namePattern = toNamePattern(q);
        Slice<UserFollows> slice = userFollowsRepository.findFriends(userId, namePattern,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId, toPageable(limit));

        return new FollowsActivity(slice, Set.copyOf(idsOf(slice, UserFollows::getFollowed)),
                userFollowsRepository.countFriends(userId, namePattern));
    }

    /** person: el otro usuario de cada follow. */
    private static List<UUID> idsOf(Slice<UserFollows> slice, Function<UserFollows, Users> person) {
        return slice.getContent().stream().map(follow -> person.apply(follow).getId()).toList();
    }

    /** «ana» → «%ana%». % y _ escapados con ! para que se busquen tal cual (ver ESCAPE '!' en la query). */
    static String toNamePattern(String q) {
        if (!StringUtils.hasText(q)) {
            return "%";
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** Siempre pagina 0: con keyset el desplazamiento lo da el cursor, no el offset. */
    private Pageable toPageable(int limit) {
        return PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
    }

    private static EntityNotFoundException userNotFound(UUID userId) {
        return new EntityNotFoundException("User not found with id " + userId);
    }
}
