package com.kryptosystems.ballastasera.cache;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.models.dtos.CityDto;
import com.kryptosystems.ballastasera.models.mappers.CitiesMapper;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class CitiesCache {

    private final CitiesService citiesService;
    private final CitiesMapper citiesMapper;

    @Cacheable(cacheNames = RedisCacheConfig.CITY_LIST, key = "'all'")
    public List<CityDto> findActive() {
        log.info("Caching getCities");
        return citiesService.findActive().stream()
                .map(citiesMapper::toDto)
                .toList();
    }
}
