package com.kryptosystems.ballastasera.cache;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.models.dtos.VenueCreateDto;
import com.kryptosystems.ballastasera.models.dtos.VenueMapPinDto;
import com.kryptosystems.ballastasera.models.dtos.VenueUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapperImpl;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.implementations.VenuesServiceImpl;
import com.kryptosystems.ballastasera.services.manager.GeocodingService;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache de los pines del mapa (GET /rest/venues/map) con un cache en memoria en vez de Redis.
 * VenuesServiceImpl es el real (con sus @CacheEvict) y los repositorios son mocks, asi que
 * "ir a la base de datos" se mide contando las llamadas a venuesRepository.findByCityId.
 */
@SpringJUnitConfig
@MockitoBean(types = {OrganizersRepository.class, EventsRepository.class, GeocodingService.class,
        UsersService.class, ObjectStorageService.class, WebpConverterService.class})
class VenuesCacheTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CITY_VENUE_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final Long CITY_ID = 1L;
    private static final Long OTHER_CITY_ID = 2L;
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Configuration
    @EnableCaching
    @Import({VenuesCache.class, VenuesMapperImpl.class, VenuesServiceImpl.class})
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private VenuesCache venuesCache;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private VenuesService venuesService;

    @MockitoBean
    private VenuesRepository venuesRepository;

    @MockitoBean
    private CitiesRepository citiesRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(RedisCacheConfig.VENUE_MAP_BY_CITY).clear();

        Cities city = new Cities();
        city.setId(CITY_ID);
        city.setName("Milano");

        when(venuesRepository.findByCityId(CITY_ID)).thenReturn(List.of(venue(VENUE_ID, "Sala Havana")));
        when(venuesRepository.findByCityId(OTHER_CITY_ID)).thenReturn(List.of(venue(OTHER_CITY_VENUE_ID, "Club Tropicana")));
        when(venuesRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue(VENUE_ID, "Sala Havana")));
        when(citiesRepository.findById(CITY_ID)).thenReturn(Optional.of(city));
    }

    @Test
    void secondReadOfMapPinsComesFromCache() {
        venuesCache.findByCityId(CITY_ID);
        List<VenueMapPinDto> cached = venuesCache.findByCityId(CITY_ID);

        assertThat(cached).extracting(VenueMapPinDto::getId).containsExactly(VENUE_ID);
        verify(venuesRepository, times(1)).findByCityId(CITY_ID);
    }

    @Test
    void mapPinsAreCachedPerCity() {
        venuesCache.findByCityId(CITY_ID);
        venuesCache.findByCityId(OTHER_CITY_ID);

        assertThat(venuesCache.findByCityId(CITY_ID)).extracting(VenueMapPinDto::getId).containsExactly(VENUE_ID);
        assertThat(venuesCache.findByCityId(OTHER_CITY_ID)).extracting(VenueMapPinDto::getId).containsExactly(OTHER_CITY_VENUE_ID);
        verify(venuesRepository, times(1)).findByCityId(CITY_ID);
        verify(venuesRepository, times(1)).findByCityId(OTHER_CITY_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("venueWrites")
    void venueWriteEvictsMapPins(String name, Consumer<VenuesCacheTest> write) {
        venuesCache.findByCityId(CITY_ID);

        write.accept(this);
        venuesCache.findByCityId(CITY_ID);

        verify(venuesRepository, times(2)).findByCityId(CITY_ID);
    }

    private static Stream<Arguments> venueWrites() {
        return Stream.of(
                write("create", t -> t.venuesService.createVenueAsAdmin(ADMIN_ID, createDto())),
                write("update", t -> t.venuesService.updateVenueAsAdmin(VENUE_ID, new VenueUpdateDto())),
                write("updateLogo", t -> t.venuesService.updateLogoAsAdmin(VENUE_ID, new MockMultipartFile("file", JPEG_BYTES))),
                write("deleteLogo", t -> t.venuesService.deleteLogoAsAdmin(VENUE_ID)),
                write("delete", t -> t.venuesService.deleteVenue(VENUE_ID)));
    }

    private static Arguments write(String name, Consumer<VenuesCacheTest> write) {
        return Arguments.of(name, write);
    }

    private static VenueCreateDto createDto() {
        VenueCreateDto dto = new VenueCreateDto();
        dto.setCityId(CITY_ID);
        dto.setName("Nuevo Club");
        dto.setAddress("Via Roma 1");
        dto.setLatitude(45.4642);
        dto.setLongitude(9.19);
        return dto;
    }

    private static Venues venue(UUID id, String name) {
        Venues venue = new Venues();
        venue.setId(id);
        venue.setName(name);
        return venue;
    }
}
