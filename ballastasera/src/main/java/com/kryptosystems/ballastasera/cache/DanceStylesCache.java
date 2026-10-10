package com.kryptosystems.ballastasera.cache;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.models.dtos.DanceStyleDto;
import com.kryptosystems.ballastasera.models.mappers.DanceStylesMapper;
import com.kryptosystems.ballastasera.services.manager.DanceStylesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DanceStylesCache {

    private final DanceStylesService danceStylesService;
    private final DanceStylesMapper danceStylesMapper;

    @Cacheable(cacheNames = RedisCacheConfig.DANCE_STYLE_LIST, key = "'all'")
    public List<DanceStyleDto> getAllDanceStyles() {
        log.info("Caching getAllDanceStyles");
        return danceStylesService.findAll().stream()
                .map(danceStylesMapper::toDto)
                .toList();
    }
}
