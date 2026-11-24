package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.dtos.FeedActivity;
import com.kryptosystems.ballastasera.models.dtos.SuggestionsActivity;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kryptosystems.ballastasera.utilities.EventTimingUtils.DEFAULT_DURATION;

@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    /** «Stasera» acaba a esta hora de la mañana siguiente. */
    private static final LocalTime NIGHT_ENDS_AT = LocalTime.of(6, 0);
    /** «Novità»: lo de los ultimos 7 dias, y «va a X» a serate de los proximos 7. */
    private static final Duration NEWS_WINDOW = Duration.ofDays(7);
    private static final int MAX_NEWS = 50;
    private static final int MAX_SUGGESTIONS = 20;

    private final EventAttendanceRepository eventAttendanceRepository;
    private final UserFollowsRepository userFollowsRepository;
    private final UsersRepository usersRepository;

    /** Hasta seis queries, sin N+1: las tres listas, las serate de quien mira y sus amigos entre esas personas (dos). */
    @Override
    @Transactional(readOnly = true)
    public FeedActivity findFeed(UUID viewerId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tonightUntil = tonightUntil(now);
        OffsetDateTime since = now.minus(NEWS_WINDOW);

        List<EventAttendance> tonight = eventAttendanceRepository.findFollowedGoingTonight(viewerId, now,
                now.minus(DEFAULT_DURATION), tonightUntil, EventStatus.PUBLISHED,
                ContactVisibility.EVERYONE, ContactVisibility.MUTUALS);
        List<EventAttendance> goingSoon = eventAttendanceRepository.findFollowedGoingSoon(viewerId, since,
                tonightUntil, now.plus(NEWS_WINDOW), EventStatus.PUBLISHED,
                ContactVisibility.EVERYONE, ContactVisibility.MUTUALS, Limit.of(MAX_NEWS));
        List<UserFollows> newFollowers = userFollowsRepository.findRecentFollowers(viewerId, since, Limit.of(MAX_NEWS));

        return new FeedActivity(tonight, goingSoon, newFollowers,
                myEventIdsAmong(viewerId, tonight, goingSoon),
                friendIdsAmong(viewerId, tonight, goingSoon, newFollowers));
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestionsActivity findSuggestions(UUID viewerId, int limit) {
        List<UUID> ids = usersRepository.findSuggestionIds(viewerId, Math.clamp(limit, 1, MAX_SUGGESTIONS));
        if (ids.isEmpty()) {
            return new SuggestionsActivity(List.of(), Map.of(), Map.of(), Set.of());
        }
        // findAllById no respeta el orden: se rehace con el de la query
        Map<UUID, Users> usersById = usersRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Users::getId, Function.identity()));
        List<Users> users = ids.stream().map(usersById::get).filter(Objects::nonNull).toList();

        Map<UUID, Long> mutualFriendsCounts = userFollowsRepository.findFriendshipsWithFriendsOf(viewerId, ids).stream()
                .collect(Collectors.groupingBy(friendship -> friendship.getId().getFollowerId(), Collectors.counting()));

        // Llegan de la mas cercana a la mas lejana: se queda la primera, salvo que una posterior tenga «Cerco qualcuno»
        OffsetDateTime now = OffsetDateTime.now();
        Map<UUID, EventAttendance> sharedEvents = new HashMap<>();
        eventAttendanceRepository.findSharedUpcoming(viewerId, ids, now, now.minus(DEFAULT_DURATION),
                        EventStatus.PUBLISHED, ContactVisibility.EVERYONE)
                .forEach(attendance -> sharedEvents.merge(attendance.getId().getUserId(), attendance,
                        (current, next) -> !current.isLookingForPartner() && next.isLookingForPartner() ? next : current));

        return new SuggestionsActivity(users, mutualFriendsCounts, sharedEvents,
                userFollowsRepository.findFollowerIdsAmong(viewerId, ids));
    }

    /** Proximas 06:00 hora de Italia: a las 23:00 son las de mañana, a las 02:00 las de dentro de 4 horas. */
    static OffsetDateTime tonightUntil(OffsetDateTime now) {
        ZonedDateTime italyNow = now.atZoneSameInstant(ITALY);
        ZonedDateTime cutoff = italyNow.toLocalDate().atTime(NIGHT_ENDS_AT).atZone(ITALY);
        return (cutoff.isAfter(italyNow) ? cutoff : cutoff.plusDays(1)).toOffsetDateTime();
    }

    private Set<UUID> myEventIdsAmong(UUID viewerId, List<EventAttendance> tonight, List<EventAttendance> goingSoon) {
        Set<UUID> eventIds = Stream.concat(tonight.stream(), goingSoon.stream())
                .map(attendance -> attendance.getId().getEventId())
                .collect(Collectors.toSet());
        return eventIds.isEmpty() ? Set.of() : eventAttendanceRepository.findEventIdsAmong(viewerId, eventIds);
    }

    /** A quienes sigue, son amigos si lo siguen tambien; a sus seguidores, si los sigue el. */
    private Set<UUID> friendIdsAmong(UUID viewerId, List<EventAttendance> tonight, List<EventAttendance> goingSoon,
                                     List<UserFollows> newFollowers) {
        Set<UUID> followed = Stream.concat(tonight.stream(), goingSoon.stream())
                .map(attendance -> attendance.getId().getUserId())
                .collect(Collectors.toSet());
        Set<UUID> followers = newFollowers.stream()
                .map(follow -> follow.getId().getFollowerId())
                .collect(Collectors.toSet());

        Set<UUID> friends = new HashSet<>();
        if (!followed.isEmpty()) {
            friends.addAll(userFollowsRepository.findFollowerIdsAmong(viewerId, followed));
        }
        if (!followers.isEmpty()) {
            friends.addAll(userFollowsRepository.findFollowedIdsAmong(viewerId, followers));
        }
        return friends;
    }
}
