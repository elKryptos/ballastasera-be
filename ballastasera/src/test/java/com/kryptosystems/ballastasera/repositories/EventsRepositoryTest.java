package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.enums.EventStatus;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    @Autowired
    private EventsRepository eventsRepository;

    @Autowired
    private OrganizersRepository organizersRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CitiesRepository citiesRepository;

    @Test
    void manageableEventsArePagedOrderedAndFilteredByStatus() {
        String suffix = UUID.randomUUID().toString();
        Users user = usersRepository.saveAndFlush(user(suffix));
        Organizers organizer = organizersRepository.saveAndFlush(organizer(user, suffix));
        Cities city = citiesRepository.saveAndFlush(city(suffix));
        OffsetDateTime now = OffsetDateTime.now();

        Events oldest = eventsRepository.save(event(
                organizer, city, "Old published " + suffix, "old-published-" + suffix,
                EventStatus.PUBLISHED, now.minusDays(10)));
        Events pending = eventsRepository.save(event(
                organizer, city, "Pending " + suffix, "pending-" + suffix,
                EventStatus.PENDING, now.minusDays(2)));
        Events newest = eventsRepository.save(event(
                organizer, city, "New published " + suffix, "new-published-" + suffix,
                EventStatus.PUBLISHED, now.plusDays(2)));
        eventsRepository.flush();

        var firstPage = eventsRepository.findManageableByOrganizerId(
                organizer.getId(), PageRequest.of(0, 2));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(newest.getId(), firstPage.getContent().get(0).getId());
        assertEquals(pending.getId(), firstPage.getContent().get(1).getId());

        var published = eventsRepository.findManageableByOrganizerIdAndStatus(
                organizer.getId(), EventStatus.PUBLISHED, PageRequest.of(0, 10));

        assertEquals(2, published.getTotalElements());
        assertEquals(newest.getId(), published.getContent().get(0).getId());
        assertEquals(oldest.getId(), published.getContent().get(1).getId());
        assertTrue(published.getContent().stream()
                .allMatch(event -> event.getStatus() == EventStatus.PUBLISHED));
    }

    private Users user(String suffix) {
        Users user = new Users();
        user.setEmail("events-repository-" + suffix + "@example.com");
        user.setDisplayName("Events Repository Test");
        return user;
    }

    private Organizers organizer(Users user, String suffix) {
        Organizers organizer = new Organizers();
        organizer.setUser(user);
        organizer.setName("Repository Organizer " + suffix);
        organizer.setSlug("repository-organizer-" + suffix);
        organizer.setVerified(true);
        organizer.setClaimed(true);
        return organizer;
    }

    private Cities city(String suffix) {
        Cities city = new Cities();
        city.setName("Repository City " + suffix);
        city.setSlug("repository-city-" + suffix);
        return city;
    }

    private Events event(Organizers organizer, Cities city, String title, String slug,
                         EventStatus status, OffsetDateTime startAt) {
        Events event = new Events();
        event.setOrganizer(organizer);
        event.setCity(city);
        event.setTitle(title);
        event.setSlug(slug);
        event.setAddress("Via Repository 1");
        event.setLatitude(45.4642);
        event.setLongitude(9.1900);
        event.setStartAt(startAt);
        event.setEndAt(startAt.plusHours(3));
        event.setStatus(status);
        return event;
    }
}
