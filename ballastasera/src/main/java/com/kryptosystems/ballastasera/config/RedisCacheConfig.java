package com.kryptosystems.ballastasera.config;

import com.kryptosystems.ballastasera.models.dtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    public static final String VENUE_DETAIL = "venueDetail";
    public static final String VENUE_BY_CITY = "venueByCity";
    public static final String VENUE_MAP_BY_CITY = "venueMapByCity";
    public static final String EVENT_DETAIL = "eventDetail";
    public static final String CITY_LIST = "cityList";
    public static final String DANCE_STYLE_LIST = "danceStyleList";

    @Value("${app.cache.venues-ttl}")
    private Duration venuesTtl;

    @Value("${app.cache.events-ttl}")
    private Duration eventsTtl;

    @Value("${app.cache.catalogs-ttl}")
    private Duration catalogsTtl;

    @Bean
    public RedisCacheManagerBuilderCustomizer venuesCacheCustomizer(JsonMapper jsonMapper) {
        var typeFactory = jsonMapper.getTypeFactory();
        return builder -> builder
                .withCacheConfiguration(VENUE_DETAIL, jsonCache(jsonMapper, typeFactory.constructType(VenueDetailDto.class), venuesTtl))
                .withCacheConfiguration(VENUE_BY_CITY, jsonCache(jsonMapper, typeFactory.constructCollectionType(List.class, VenuesSummaryDto.class), venuesTtl))
                .withCacheConfiguration(VENUE_MAP_BY_CITY, jsonCache(jsonMapper, typeFactory.constructCollectionType(List.class, VenueMapPinDto.class), venuesTtl))
                .withCacheConfiguration(EVENT_DETAIL, jsonCache(jsonMapper, typeFactory.constructType(EventDetailDto.class), eventsTtl))
                .withCacheConfiguration(CITY_LIST, jsonCache(jsonMapper, typeFactory.constructCollectionType(List.class, CityDto.class), catalogsTtl))
                .withCacheConfiguration(DANCE_STYLE_LIST, jsonCache(jsonMapper, typeFactory.constructCollectionType(List.class, DanceStyleDto.class), catalogsTtl));
    }

    private RedisCacheConfiguration jsonCache(JsonMapper jsonMapper, JavaType type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, type)
                ));
    }

    //** Si Redis no responde, se logue y el GET sigue contra la base de datos en vez de dar 500. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
