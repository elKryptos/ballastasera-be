package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.exceptions.SocialActionException;
import com.kryptosystems.ballastasera.models.dtos.CursorPageDto;
import com.kryptosystems.ballastasera.models.dtos.SocialCounts;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserFollowId;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.UserBlocksRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.FollowsService;
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
    private final UserMapper userMapper;

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
        UserFollows follow = new UserFollows();
        follow.setId(id);
        follow.setFollower(usersRepository.getReferenceById(followerId));
        follow.setFollowed(followed);
        userFollowsRepository.save(follow);
    }

    @Override
    @Transactional
    public void unfollow(UUID followerId, UUID followedId) {
        userFollowsRepository.deleteById(UserFollowId.of(followerId, followedId));
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

    @Override
    public SocialCounts countSocial(UUID userId) {
        return new SocialCounts(
                userFollowsRepository.countFollowers(userId),
                userFollowsRepository.countFollowing(userId),
                eventAttendanceRepository.countPastByUserId(userId));
    }

    /** Todos siguen a userId: la relacion es MUTUAL si userId los sigue tambien, si no FOLLOWED_BY. */
    @Override
    @Transactional(readOnly = true)
    public CursorPageDto<UserSummaryDto> findFollowers(UUID userId, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        Slice<UserFollows> slice = userFollowsRepository.findFollowers(userId,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId, toPageable(limit));

        Set<UUID> followedBack = followedByAmong(userId, slice, UserFollows::getFollower);
        return toCursorPage(slice, UserFollows::getFollower,
                person -> SocialRelation.of(followedBack.contains(person.getId()), true),
                userFollowsRepository.countFollowers(userId));
    }

    /** userId sigue a todos: la relacion es MUTUAL si lo siguen tambien, si no FOLLOWING. */
    @Override
    @Transactional(readOnly = true)
    public CursorPageDto<UserSummaryDto> findFollowing(UUID userId, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        Slice<UserFollows> slice = userFollowsRepository.findFollowing(userId,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId, toPageable(limit));

        List<UUID> ids = slice.getContent().stream().map(follow -> follow.getFollowed().getId()).toList();
        Set<UUID> followBack = ids.isEmpty() ? Set.of() : userFollowsRepository.findFollowerIdsAmong(userId, ids);
        return toCursorPage(slice, UserFollows::getFollowed,
                person -> SocialRelation.of(true, followBack.contains(person.getId())),
                userFollowsRepository.countFollowing(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageDto<UserSummaryDto> findFriends(UUID userId, String q, OffsetDateTime cursorAt, UUID cursorId, int limit) {
        boolean firstPage = cursorAt == null || cursorId == null;
        String namePattern = toNamePattern(q);
        Slice<UserFollows> slice = userFollowsRepository.findFriends(userId, namePattern,
                firstPage ? MAX_CREATED_AT : cursorAt, firstPage ? MAX_ID : cursorId, toPageable(limit));

        return toCursorPage(slice, UserFollows::getFollowed, person -> SocialRelation.MUTUAL,
                userFollowsRepository.countFriends(userId, namePattern));
    }

    /** De las personas de la pagina, a quienes sigue userId: una sola query para toda la pagina (sin N+1). */
    private Set<UUID> followedByAmong(UUID userId, Slice<UserFollows> slice, Function<UserFollows, Users> person) {
        List<UUID> ids = slice.getContent().stream().map(follow -> person.apply(follow).getId()).toList();
        return ids.isEmpty() ? Set.of() : userFollowsRepository.findFollowedIdsAmong(userId, ids);
    }

    /** person: el otro usuario de cada follow. El cursor es la fecha del follow y el id de esa persona. */
    private CursorPageDto<UserSummaryDto> toCursorPage(Slice<UserFollows> slice,
                                                       Function<UserFollows, Users> person,
                                                       Function<Users, SocialRelation> relation,
                                                       long total) {
        List<UserSummaryDto> items = slice.getContent().stream()
                .map(person)
                .map(user -> userMapper.toSummaryDto(user, relation.apply(user)))
                .toList();
        CursorPageDto<UserSummaryDto> dto = new CursorPageDto<>();
        dto.setItems(items);
        dto.setTotal(total);
        if (slice.hasNext() && !slice.isEmpty()) {
            UserFollows last = slice.getContent().getLast();
            dto.setNextCursorStartAt(last.getCreatedAt());
            dto.setNextCursorId(person.apply(last).getId());
        }
        return dto;
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
