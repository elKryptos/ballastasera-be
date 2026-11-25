package com.kryptosystems.ballastasera.services.implementations;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.ProfileActivity;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.services.manager.FollowsService;
import com.kryptosystems.ballastasera.services.manager.UserActivityService;
import com.kryptosystems.ballastasera.services.manager.UsersService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private static final int MAX_LIMIT = 50;

    /** Cursores de primera página: antes/después de cualquier evento posible.
     * Evitan pasar null a la query nativa (Postgres no infiere el tipo de un parámetro null).
     * Upcoming va ASC y arranca desde el mínimo, past va DESC y arranca desde el máximo. */
    private static final OffsetDateTime MIN_START_AT = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MAX_START_AT = OffsetDateTime.of(2030, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID MIN_ID = new UUID(0L, 0L);
    private static final UUID MAX_ID = new UUID(-1L, -1L);

    /** «Avete in comune» muestra hasta 3 caras; el total va aparte. */
    private static final int MUTUAL_FRIENDS_SHOWN = 3;

    private final FavoritesRepository favoritesRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsRepository eventsRepository;
    private final UserFollowsRepository userFollowsRepository;
    private final UsersService usersService;
    private final FollowsService followsService;

    @Override
    public List<UUID> findMyFavoritesEventsIds(UUID userId) {
        return favoritesRepository.findEventsIdsByUserId(userId);
    }

    @Override
    public Slice<Events> findMyFavorites(UUID userId, boolean past, OffsetDateTime cursorStartAt, UUID cursorId, int limit) {
        boolean firstPage = cursorStartAt == null || cursorId == null;
        OffsetDateTime startAt = firstPage ? (past ? MAX_START_AT : MIN_START_AT) : cursorStartAt;
        UUID id = firstPage ? (past ? MAX_ID : MIN_ID) : cursorId;

        Slice<UUID> ids = past
                ? favoritesRepository.findPastEventsIdsByUserId(userId, startAt, id, toPageable(limit))
                : favoritesRepository.findUpcomingEventsIdsByUserId(userId, startAt, id, toPageable(limit));
        return loadInOrder(ids);
    }

    @Override
    public long countMyFavorites(UUID userId, boolean past) {
        return past
                ? favoritesRepository.countPastByUserId(userId)
                : favoritesRepository.countUpcomingByUserId(userId);
    }

    @Override
    public Slice<Events> findMyAttendance(UUID userId, boolean past, OffsetDateTime cursorStartAt, UUID cursorId, int limit) {
        boolean firstPage = cursorStartAt == null || cursorId == null;
        OffsetDateTime startAt = firstPage ? (past ? MAX_START_AT : MIN_START_AT) : cursorStartAt;
        UUID id = firstPage ? (past ? MAX_ID : MIN_ID) : cursorId;

        Slice<UUID> ids = past
                ? eventAttendanceRepository.findPastEventsIdsByUserId(userId, startAt, id, toPageable(limit))
                : eventAttendanceRepository.findUpcomingEventsIdsByUserId(userId, startAt, id, toPageable(limit));
        return loadInOrder(ids);
    }

    @Override
    public long countMyAttendance(UUID userId, boolean past) {
        return past
                ? eventAttendanceRepository.countPastByUserId(userId)
                : eventAttendanceRepository.countUpcomingByUserId(userId);
    }

    @Override
    public boolean canSeeAttendanceOf(UUID viewerId, UUID userId) {
        Users user = usersService.findPublicProfile(viewerId, userId);
        return canSeeAttendance(viewerId, user, followsService.findRelation(viewerId, userId));
    }

    /** Las serate juntos y el pallino dicen a que serate va el dueño: solo si quien mira puede verlas. */
    @Override
    public ProfileActivity findProfileActivity(UUID viewerId, Users user, SocialRelation relation) {
        UUID userId = user.getId();
        boolean canSeeAttendance = canSeeAttendance(viewerId, user, relation);
        boolean lookingForPartnerToday = canSeeAttendance && eventAttendanceRepository.existsLookingForPartnerSoon(userId);
        if (userId.equals(viewerId)) {
            return new ProfileActivity(List.of(), 0, 0, null, lookingForPartnerToday);
        }

        List<Users> mutualFriends = userFollowsRepository.findMutualFriends(viewerId, userId, Limit.of(MUTUAL_FRIENDS_SHOWN));
        long mutualFriendsCount = mutualFriends.size() < MUTUAL_FRIENDS_SHOWN
                ? mutualFriends.size()
                : userFollowsRepository.countMutualFriends(viewerId, userId);
        if (!canSeeAttendance) {
            return new ProfileActivity(mutualFriends, mutualFriendsCount, 0, null, false);
        }
        long eventsTogether = eventAttendanceRepository.countPastTogether(viewerId, userId);
        Events lastEventTogether = eventsTogether == 0 ? null
                : eventAttendanceRepository.findLastPastEventIdTogether(viewerId, userId)
                        .flatMap(eventsRepository::findById)
                        .orElse(null);
        return new ProfileActivity(mutualFriends, mutualFriendsCount, eventsTogether, lastEventTogether,
                lookingForPartnerToday);
    }

    /** El dueño siempre ve sus serate; los demas segun attendanceVisibility y la relacion. */
    private static boolean canSeeAttendance(UUID viewerId, Users user, SocialRelation relation) {
        return user.getId().equals(viewerId) || user.getAttendanceVisibility().allows(relation);
    }

    /** Siempre pagina 0: con keyset el desplazamiento lo da el cursor, no el offset. */
    private Pageable toPageable(int limit) {
        return PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
    }

    /** Carga organizer/venue/danceStyles en una sola query y reaplica el orden de los ids. */
    private Slice<Events> loadInOrder(Slice<UUID> ids) {
        if (!ids.hasContent()) {
            return new SliceImpl<>(List.of(), ids.getPageable(), false);
        }
        Map<UUID, Events> eventsById = eventsRepository.findAllWithDetailsByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Events::getId, e -> e));

        List<Events> events = ids.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .toList();
        return new SliceImpl<>(events, ids.getPageable(), ids.hasNext());
    }
}
