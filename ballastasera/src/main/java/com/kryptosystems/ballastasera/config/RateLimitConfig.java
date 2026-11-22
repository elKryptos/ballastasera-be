package com.kryptosystems.ballastasera.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    /**
     * Cliente Lettuce propio para Bucket4j, que necesita uno nativo (el de Spring Data Redis no se expone).
     * Mismo Redis y mismos timeouts que la cache. Crearlo no conecta: conecta RateLimitService en la
     * primera request, asi el BE arranca aunque Redis este caido.
     * REJECT_COMMANDS: con Redis caido cada comando falla al momento en vez de esperar el timeout.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(@Value("${spring.data.redis.host}") String host,
                                            @Value("${spring.data.redis.port}") int port,
                                            @Value("${spring.data.redis.password:}") String password,
                                            @Value("${spring.data.redis.timeout}") Duration timeout,
                                            @Value("${spring.data.redis.connect-timeout}") Duration connectTimeout) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(timeout);
        if (StringUtils.hasText(password)) {
            uri.withPassword(password.toCharArray());
        }

        RedisClient client = RedisClient.create(uri.build());
        client.setOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .build());
        return client;
    }
}
