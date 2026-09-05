package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.jwt.secret=test-jwt-secret-key-with-at-least-32-characters",
        "app.jwt.expiration-ms=86400000",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "app.frontend.oauth2-redirect-uri=http://localhost:4200/oauth2/callback",
        "resend.api-key=test-api-key",
        "resend.from-email=test@example.com",
        "frontend.url=http://localhost:4200"
})
@Transactional
class EventsRepositoryTest {

    private static final UUID FUTURE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID LIVE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID NO_END_LIVE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID TIE_FIRST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000040");
    private static final UUID TIE_SECOND_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000050");
    private static final UUID PENDING_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000060");
    private static final UUID CANCELLED_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000070");
    private static final UUID EXPIRED_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000080");
    private static final UUID OTHER_CITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000090");
    private static final UUID COUNT_EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID SECOND_COUNT_EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b0");

    @Autowired
    private CitiesRepository citiesRepository;

    @Autowired
    private EventsRepository eventsRepository;

    @Autowired
    private EventAttendanceRepository eventAttendanceRepository;

    @Autowired
    private OrganizersRepository organizersRepository;

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void publicEventIdsFilterVisibilityCityAndEffectiveEnd() {
        OffsetDateTime now = OffsetDateTime.now();
        Cities city = saveCity("repository-events-main", true);
        Cities otherCity = saveCity("repository-events-other", true);
        Organizers organizer = saveOrganizer("repository-events-organizer");

        Events future = event(
                FUTURE_ID,
                city,
                organizer,
                "repository-events-future",
                now.plusDays(1),
                now.plusDays(1).plusHours(3),
                EventStatus.PUBLISHED
        );
        Events live = event(
                LIVE_ID,
                city,
                organizer,
                "repository-events-live",
                now.minusHours(1),
                now.plusHours(1),
                EventStatus.PUBLISHED
        );
        Events noEndLive = event(
                NO_END_LIVE_ID,
                city,
                organizer,
                "repository-events-no-end-live",
                now.minusHours(2),
                null,
                EventStatus.PUBLISHED
        );
        Events pending = event(
                PENDING_ID,
                city,
                organizer,
                "repository-events-pending",
                now.plusHours(1),
                now.plusHours(3),
                EventStatus.PENDING
        );
        Events cancelled = event(
                CANCELLED_ID,
                city,
                organizer,
                "repository-events-cancelled",
                now.plusHours(2),
                now.plusHours(4),
                EventStatus.CANCELLED
        );
        Events expired = event(
                EXPIRED_ID,
                city,
                organizer,
                "repository-events-expired",
                now.minusHours(5),
                null,
                EventStatus.PUBLISHED
        );
        Events otherCityEvent = event(
                OTHER_CITY_ID,
                otherCity,
                organizer,
                "repository-events-other-city",
                now.plusHours(1),
                now.plusHours(2),
                EventStatus.PUBLISHED
        );

        List.of(
                future,
                live,
                noEndLive,
                pending,
                cancelled,
                expired,
                otherCityEvent
        ).forEach(this::insertEvent);
        entityManager.flush();

        Page<UUID> result = eventsRepository.findPublicEventIdsByCity(
                city.getId(),
                now,
                PageRequest.of(0, 20)
        );

        assertEquals(
                List.of(NO_END_LIVE_ID, LIVE_ID, FUTURE_ID),
                result.getContent()
        );
        assertEquals(3, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(3, result.getNumberOfElements());
        assertTrue(result.isFirst());
    }

    @Test
    void publicEventIdsUseStableIdOrderAndPageMetadata() {
        OffsetDateTime now = OffsetDateTime.now();
        Cities city = saveCity("repository-events-order", true);
        Organizers organizer = saveOrganizer("repository-events-order-organizer");
        OffsetDateTime sameStart = now.plusDays(2);

        Events first = event(
                TIE_FIRST_ID,
                city,
                organizer,
                "repository-events-tie-first",
                sameStart,
                sameStart.plusHours(3),
                EventStatus.PUBLISHED
        );
        Events second = event(
                TIE_SECOND_ID,
                city,
                organizer,
                "repository-events-tie-second",
                sameStart,
                sameStart.plusHours(3),
                EventStatus.PUBLISHED
        );
        Events third = event(
                UUID.fromString("00000000-0000-0000-0000-000000000055"),
                city,
                organizer,
                "repository-events-order-third",
                sameStart.plusHours(1),
                sameStart.plusHours(4),
                EventStatus.PUBLISHED
        );

        List.of(first, second, third).forEach(this::insertEvent);
        entityManager.flush();

        Page<UUID> result = eventsRepository.findPublicEventIdsByCity(
                city.getId(),
                now,
                PageRequest.of(1, 2)
        );

        assertEquals(3, result.getTotalElements());
        assertEquals(List.of(third.getId()), result.getContent());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(1, result.getNumberOfElements());
    }

    @Test
    void goingCountsGroupByEventAndIgnoreInterestedAttendance() {
        OffsetDateTime now = OffsetDateTime.now();
        Cities city = saveCity("repository-events-counts", true);
        Organizers organizer = saveOrganizer("repository-events-counts-organizer");
        Events firstEvent = event(
                COUNT_EVENT_ID,
                city,
                organizer,
                "repository-events-count-first",
                now.plusDays(1),
                now.plusDays(1).plusHours(3),
                EventStatus.PUBLISHED
        );
        Events secondEvent = event(
                SECOND_COUNT_EVENT_ID,
                city,
                organizer,
                "repository-events-count-second",
                now.plusDays(2),
                now.plusDays(2).plusHours(3),
                EventStatus.PUBLISHED
        );
        insertEvent(firstEvent);
        insertEvent(secondEvent);
        entityManager.flush();

        Users firstUser = saveUser("repository-count-first@example.com");
        Users secondUser = saveUser("repository-count-second@example.com");
        Users interestedUser = saveUser("repository-count-interested@example.com");
        saveAttendance(firstUser, firstEvent, AttendanceStatus.GOING);
        saveAttendance(secondUser, firstEvent, AttendanceStatus.GOING);
        saveAttendance(interestedUser, firstEvent, AttendanceStatus.INTERESTED);
        saveAttendance(firstUser, secondEvent, AttendanceStatus.GOING);
        entityManager.flush();

        List<Object[]> rows = eventAttendanceRepository.countByEventIdInAndStatus(
                List.of(COUNT_EVENT_ID, SECOND_COUNT_EVENT_ID),
                AttendanceStatus.GOING
        );

        assertEquals(2, rows.size());
        assertEquals(2L, countFor(COUNT_EVENT_ID, rows));
        assertEquals(1L, countFor(SECOND_COUNT_EVENT_ID, rows));
    }

    private Users saveUser(String email) {
        Users user = new Users();
        user.setEmail(email);
        user.setDisplayName(email);
        return usersRepository.saveAndFlush(user);
    }

    private void saveAttendance(Users user, Events event, AttendanceStatus status) {
        EventAttendance attendance = new EventAttendance();
        UserEventId id = new UserEventId();
        id.setUserId(user.getId());
        id.setEventId(event.getId());
        attendance.setId(id);
        attendance.setUser(user);
        attendance.setEvent(entityManager.getReference(Events.class, event.getId()));
        attendance.setStatus(status);
        eventAttendanceRepository.save(attendance);
    }

    private long countFor(UUID eventId, List<Object[]> rows) {
        return rows.stream()
                .filter(row -> eventId.equals(row[0]))
                .mapToLong(row -> ((Number) row[1]).longValue())
                .findFirst()
                .orElse(0L);
    }

    private Cities saveCity(String slug, boolean active) {
        Cities city = new Cities();
        city.setName(slug);
        city.setSlug(slug);
        city.setActive(active);
        return citiesRepository.saveAndFlush(city);
    }

    private Organizers saveOrganizer(String slug) {
        Organizers organizer = new Organizers();
        organizer.setName(slug);
        organizer.setSlug(slug);
        organizer.setVerified(true);
        return organizersRepository.saveAndFlush(organizer);
    }

    private Events event(
            UUID id,
            Cities city,
            Organizers organizer,
            String slug,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            EventStatus status
    ) {
        Events event = new Events();
        event.setId(id);
        event.setCity(city);
        event.setOrganizer(organizer);
        event.setTitle(slug);
        event.setSlug(slug);
        event.setStartAt(startAt);
        event.setEndAt(endAt);
        event.setStatus(status);
        event.setAddress("Via Repository Test 1");
        event.setLatitude(45.4642);
        event.setLongitude(9.1900);
        return event;
    }

    private void insertEvent(Events event) {
        entityManager.createNativeQuery("""
                INSERT INTO events (
                    id, organizer_id, city_id, title, slug, start_at, end_at,
                    is_free, currency, address, latitude, longitude, status
                ) VALUES (
                    :id, :organizerId, :cityId, :title, :slug, :startAt,
                    CAST(:endAt AS timestamptz), TRUE, 'EUR', :address,
                    :latitude, :longitude, CAST(:status AS event_status)
                )
                """)
                .setParameter("id", event.getId())
                .setParameter("organizerId", event.getOrganizer().getId())
                .setParameter("cityId", event.getCity().getId())
                .setParameter("title", event.getTitle())
                .setParameter("slug", event.getSlug())
                .setParameter("startAt", event.getStartAt())
                .setParameter("endAt", event.getEndAt())
                .setParameter("address", event.getAddress())
                .setParameter("latitude", event.getLatitude())
                .setParameter("longitude", event.getLongitude())
                .setParameter("status", event.getStatus().name())
                .executeUpdate();
    }
}
