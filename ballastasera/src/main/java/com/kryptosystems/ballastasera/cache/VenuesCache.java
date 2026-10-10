package com.kryptosystems.ballastasera.cache;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.models.dtos.VenueDetailDto;
import com.kryptosystems.ballastasera.models.dtos.VenueMapPinDto;
import com.kryptosystems.ballastasera.models.dtos.VenuesSummaryDto;
import com.kryptosystems.ballastasera.models.mappers.VenuesMapper;
import com.kryptosystems.ballastasera.services.manager.VenuesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class VenuesCache {

    private final VenuesService venuesService;
    private final VenuesMapper venuesMapper;

    @Cacheable(cacheNames = RedisCacheConfig.VENUE_DETAIL, key = "#id")
    public VenueDetailDto findById(UUID id) {
        log.info("Caching getVenueDetial, starting for " + id);
        return venuesMapper.toVenueDetailDto(venuesService.findById(id));
    }

    @Cacheable(cacheNames = RedisCacheConfig.VENUE_BY_CITY, key = "#cityId",
            condition = "#search == null || #search.isBlank()")
    public List<VenuesSummaryDto> search(Long cityId, String search) {
        log.info("Caching getVenueSummary, starting for cityId: " + cityId + ", search: " + search);
        return venuesService.search(cityId, search).stream()
                .map(venuesMapper::toVenueSummaryDto)
                .toList();
    }

    @Cacheable(cacheNames = RedisCacheConfig.VENUE_MAP_BY_CITY, key = "#cityId")
    public List<VenueMapPinDto> findByCityId(Long cityId) {
        log.info("Caching getMapVenues, starting for cityId: " + cityId);
        return venuesService.findByCityId(cityId).stream()
                .map(venuesMapper::toVenueMapPinDto)
                .toList();
    }
}
