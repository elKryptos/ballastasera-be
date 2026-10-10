package com.kryptosystems.ballastasera.config;

import com.kryptosystems.ballastasera.enums.EventType;
import com.kryptosystems.ballastasera.enums.FlyerStatus;
import com.kryptosystems.ballastasera.enums.VenueType;
import com.kryptosystems.ballastasera.models.dtos.CityDto;
import com.kryptosystems.ballastasera.models.dtos.DanceStyleDto;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueMapPinDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No necesita Redis: solo usa la configuracion de cada cache (TTL y serializador JSON).
 * Si un DTO deja de poder leerse desde su JSON, en produccion no falla ninguna request
 * (LoggingCacheErrorHandler lo absorbe y se va a la base de datos): el cache simplemente
 * deja de acertar sin que nadie lo note. Este test es lo unico que lo detecta.
 */
@SpringBootTest(classes = RedisCacheConfig.class, properties = {
        "app.cache.venues-ttl=1h",
        "app.cache.events-ttl=30m",
        "app.cache.catalogs-ttl=12h"
})
@ImportAutoConfiguration({JacksonAutoConfiguration.class, DataRedisAutoConfiguration.class, CacheAutoConfiguration.class})
class RedisCacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void eachCacheUsesItsOwnTtl() {
        assertThat(ttl(RedisCacheConfig.VENUE_DETAIL)).isEqualTo(Duration.ofHours(1));
        assertThat(ttl(RedisCacheConfig.VENUE_BY_CITY)).isEqualTo(Duration.ofHours(1));
        assertThat(ttl(RedisCacheConfig.VENUE_MAP_BY_CITY)).isEqualTo(Duration.ofHours(1));
        assertThat(ttl(RedisCacheConfig.EVENT_DETAIL)).isEqualTo(Duration.ofMinutes(30));
        assertThat(ttl(RedisCacheConfig.CITY_LIST)).isEqualTo(Duration.ofHours(12));
        assertThat(ttl(RedisCacheConfig.DANCE_STYLE_LIST)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void eventDetailSurvivesJsonRoundTrip() {
        EventDetailDto event = eventDetail();

        Object cached = roundTrip(RedisCacheConfig.EVENT_DETAIL, event);

        assertThat(cached).isInstanceOf(EventDetailDto.class);
        assertThat(cached).usingRecursiveComparison().isEqualTo(event);
    }

    @Test
    void venueMapPinListSurvivesJsonRoundTrip() {
        VenueMapPinDto pin = new VenueMapPinDto();
        pin.setId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
        pin.setName("Sala Havana");
        pin.setType(VenueType.CLUB);
        pin.setLogoUrl("http://localhost/logos/venues/1/logo-a.webp");
        pin.setAddress("Via Roma 1");
        pin.setLatitude(45.4642);
        pin.setLongitude(9.19);

        Object cached = roundTrip(RedisCacheConfig.VENUE_MAP_BY_CITY, List.of(pin));

        assertThat((List<?>) cached).hasOnlyElementsOfType(VenueMapPinDto.class);
        assertThat(cached).usingRecursiveComparison().isEqualTo(List.of(pin));
    }

    @Test
    void cityListSurvivesJsonRoundTrip() {
        CityDto city = new CityDto();
        city.setId(1L);
        city.setName("Milano");
        city.setProvince("MI");
        city.setRegion("Lombardia");
        city.setCountry("IT");
        city.setLatitude(45.4642);
        city.setLongitude(9.19);
        city.setSlug("milano");
        city.setActive(true);

        Object cached = roundTrip(RedisCacheConfig.CITY_LIST, List.of(city));

        assertThat((List<?>) cached).hasOnlyElementsOfType(CityDto.class);
        assertThat(cached).usingRecursiveComparison().isEqualTo(List.of(city));
    }

    @Test
    void danceStyleListSurvivesJsonRoundTrip() {
        DanceStyleDto danceStyle = new DanceStyleDto();
        danceStyle.setId(1L);
        danceStyle.setName("Bachata");
        danceStyle.setSlug("bachata");

        Object cached = roundTrip(RedisCacheConfig.DANCE_STYLE_LIST, List.of(danceStyle));

        assertThat((List<?>) cached).hasOnlyElementsOfType(DanceStyleDto.class);
        assertThat(cached).usingRecursiveComparison().isEqualTo(List.of(danceStyle));
    }

    private Duration ttl(String cacheName) {
        return configuration(cacheName).getTtlFunction().getTimeToLive("key", null);
    }

    /** Lo mismo que hace Redis: guardar el valor como JSON y volver a leerlo. */
    private Object roundTrip(String cacheName, Object value) {
        var serializer = configuration(cacheName).getValueSerializationPair();
        return serializer.read(serializer.write(value));
    }

    private RedisCacheConfiguration configuration(String cacheName) {
        return ((RedisCacheManager) cacheManager).getCacheConfigurations().get(cacheName);
    }

    private EventDetailDto eventDetail() {
        OrganizerDetailDto organizer = new OrganizerDetailDto();
        organizer.setId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        organizer.setName("Milatino");
        organizer.setSlug("milatino");
        organizer.setType("PERSON");
        organizer.setInstagram("milatino2.0");
        organizer.setVerified(true);
        organizer.setClaimed(true);

        VenueDetailDto venue = new VenueDetailDto();
        venue.setId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
        venue.setOrganizerId(organizer.getId());
        venue.setOrganizerName("Milatino");
        venue.setCityId(1L);
        venue.setCityName("Milano");
        venue.setName("Sala Havana");
        venue.setType(VenueType.CLUB);
        venue.setAddress("Via Roma 1");
        venue.setLatitude(45.4642);
        venue.setLongitude(9.19);
        venue.setLogoUrl("http://localhost/logos/venues/1/logo-a.webp");
        venue.setCreatedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        venue.setUpdatedAt(OffsetDateTime.parse("2026-02-01T10:00:00Z"));

        EventDetailDto event = new EventDetailDto();
        event.setId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        event.setSeriesId(UUID.fromString("40000000-0000-0000-0000-000000000001"));
        event.setSlug("salsa-night");
        event.setTitle("Salsa Night");
        event.setEventType(EventType.EVENT);
        event.setDescription("Social de salsa y bachata");
        event.setFlyerUrl("http://localhost/flyers/20000000-0000-0000-0000-000000000001");
        event.setFlyerStatus(FlyerStatus.READY);
        event.setStartAt(OffsetDateTime.parse("2026-10-10T20:00:00Z"));
        event.setEndAt(OffsetDateTime.parse("2026-10-11T01:00:00Z"));
        event.setLiveNow(true);
        event.setFree(false);
        event.setPrice(new BigDecimal("12.50"));
        event.setCurrency("EUR");
        event.setAddress("Via Roma 1");
        event.setLatitude(45.4642);
        event.setLongitude(9.19);
        event.setCityName("Milano");
        event.setInstagramUrl("https://www.instagram.com/milatino2.0/");
        event.setWhatsappUrl("https://wa.me/393331234567");
        event.setLikesCount(7L);
        event.setOrganizer(organizer);
        event.setVenue(venue);
        event.setDanceStyles(List.of("Bachata", "Salsa Cubana"));
        event.setGoingCount(3);
        return event;
    }
}
