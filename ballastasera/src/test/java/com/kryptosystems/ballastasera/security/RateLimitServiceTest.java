package com.kryptosystems.ballastasera.security;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin Redis: el caso con Redis real esta en RateLimitIntegrationTest. */
class RateLimitServiceTest {

    /** Puerto 1: nadie escucha, la conexion se rechaza al momento. */
    private final RedisClient unreachableRedis = RedisClient.create(RedisURI.create("localhost", 1));

    @AfterEach
    void shutdown() {
        unreachableRedis.shutdown();
    }

    @Test
    void redisDownLetsRequestsThrough() {
        unreachableRedis.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(300)).build())
                .build());
        RateLimitService service = service(true);

        for (int i = 0; i < 5; i++) {
            assertThat(service.tryConsume(RateLimitGroup.WRITE, "user:ana").allowed()).isTrue();
        }
    }

    @Test
    void disabledAllowsEverythingWithoutTouchingRedis() {
        RateLimitService service = service(false);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.tryConsume(RateLimitGroup.SOCIAL, "user:ana").allowed()).isTrue();
    }

    private RateLimitService service(boolean enabled) {
        return new RateLimitService(unreachableRedis, enabled, 1, 1, 1, 1, 1, 1);
    }
}
