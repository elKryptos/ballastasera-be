package com.kryptosystems.ballastasera.cache;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.models.dtos.EventDetailDto;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.services.manager.EventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventsCache {

    private final EventsService eventsService;
    private final EventsMapper eventsMapper;

    /** Se cachea el DTO ya mapeado. Ojo: liveNow queda con el valor del momento en que se guardo;
     * quien llame a este metodo debe recalcularlo (lo hace EventsController).
     * Sin sync = true a propósito: no evitaba consultas simultáneas y con Redis caído convertía el 404 en un 500.*/
    @Cacheable(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#id")
    public EventDetailDto findByIdWithDetails(UUID id) {
        log.info("Caching getEventDetail, starting for id: {}", id);
        return eventsMapper.toEventDetailDto(eventsService.findByIdWithDetails(id));
    }
}
