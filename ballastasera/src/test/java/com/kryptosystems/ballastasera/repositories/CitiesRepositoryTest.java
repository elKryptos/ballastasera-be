package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Cities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
class CitiesRepositoryTest {

    @Autowired
    private CitiesRepository citiesRepository;

    @Test
    void activeCitiesAreOrderedAndInactiveCitiesAreExcluded() {
        Cities zeta = citiesRepository.saveAndFlush(city(
                "Zeta Test City", "repository-test-zeta", true));
        Cities alpha = citiesRepository.saveAndFlush(city(
                "Alpha Test City", "repository-test-alpha", true));
        Cities hidden = citiesRepository.saveAndFlush(city(
                "Hidden Test City", "repository-test-hidden", false));

        List<Long> activeIds = citiesRepository.findAllByIsActiveTrueOrderByNameAsc()
                .stream()
                .map(Cities::getId)
                .toList();

        assertTrue(activeIds.contains(alpha.getId()));
        assertTrue(activeIds.contains(zeta.getId()));
        assertTrue(activeIds.indexOf(alpha.getId()) < activeIds.indexOf(zeta.getId()));
        assertFalse(activeIds.contains(hidden.getId()));
    }

    private Cities city(String name, String slug, boolean active) {
        Cities city = new Cities();
        city.setName(name);
        city.setSlug(slug);
        city.setActive(active);
        return city;
    }
}
