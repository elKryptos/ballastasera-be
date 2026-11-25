package com.kryptosystems.ballastasera.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Buckets de Bucket4j guardados en Redis: el limite se comparte entre instancias del BE y sobrevive a un
 * reinicio. Si Redis no responde se deja pasar la request (igual que la cache con LoggingCacheErrorHandler):
 * un Redis caido no puede tumbar la API.
 */
@Slf4j
@Service
public class RateLimitService {

    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(30);
    private static final Duration KEY_TTL_MARGIN = Duration.ofSeconds(10);
    private static final RateLimitResult ALLOWED = new RateLimitResult(true, 0);

    private final RedisClient redisClient;
    private final boolean enabled;
    private final Map<RateLimitGroup, BucketConfiguration> configurations = new EnumMap<>(RateLimitGroup.class);

    private volatile ProxyManager<byte[]> proxyManager;
    private long nextConnectAttemptAt;

    public RateLimitService(RedisClient rateLimitRedisClient,
                            @Value("${app.rate-limit.enabled}") boolean enabled,
                            @Value("${app.rate-limit.read-per-minute}") long readPerMinute,
                            @Value("${app.rate-limit.write-per-minute}") long writePerMinute,
                            @Value("${app.rate-limit.auth-per-minute}") long authPerMinute,
                            @Value("${app.rate-limit.social-per-minute}") long socialPerMinute,
                            @Value("${app.rate-limit.social-per-day}") long socialPerDay,
                            @Value("${app.rate-limit.upload-per-hour}") long uploadPerHour) {
        this.redisClient = rateLimitRedisClient;
        this.enabled = enabled;
        configurations.put(RateLimitGroup.READ, config(limit(readPerMinute, Duration.ofMinutes(1))));
        configurations.put(RateLimitGroup.WRITE, config(limit(writePerMinute, Duration.ofMinutes(1))));
        configurations.put(RateLimitGroup.AUTH, config(limit(authPerMinute, Duration.ofMinutes(1))));
        configurations.put(RateLimitGroup.SOCIAL, config(
                limit(socialPerMinute, Duration.ofMinutes(1)),
                limit(socialPerDay, Duration.ofDays(1))));
        configurations.put(RateLimitGroup.UPLOAD, config(limit(uploadPerHour, Duration.ofHours(1))));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Gasta un token del bucket de esa clave en ese grupo. key: "user:{id}" o "ip:{ip}". */
    public RateLimitResult tryConsume(RateLimitGroup group, String key) {
        if (!enabled) {
            return ALLOWED;
        }
        ProxyManager<byte[]> manager = proxyManager();
        if (manager == null) {
            return ALLOWED;
        }
        try {
            ConsumptionProbe probe = manager.builder()
                    .build(bucketKey(group, key), () -> configurations.get(group))
                    .tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return ALLOWED;
            }
            return new RateLimitResult(false, toRetryAfterSeconds(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException ex) {
            log.warn("Rate limit omitido, Redis no responde: {}", ex.getMessage());
            return ALLOWED;
        }
    }

    /** Conecta en la primera request y, si Redis esta caido, lo reintenta cada RECONNECT_DELAY (no en cada
     * request). Una vez conectado, Lettuce se reconecta solo. */
    private ProxyManager<byte[]> proxyManager() {
        ProxyManager<byte[]> current = proxyManager;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (proxyManager == null && System.currentTimeMillis() >= nextConnectAttemptAt) {
                try {
                    proxyManager = Bucket4jLettuce.casBasedBuilder(redisClient)
                            .expirationAfterWrite(ExpirationAfterWriteStrategy
                                    .basedOnTimeForRefillingBucketUpToMax(KEY_TTL_MARGIN))
                            .build();
                } catch (RuntimeException ex) {
                    nextConnectAttemptAt = System.currentTimeMillis() + RECONNECT_DELAY.toMillis();
                    log.warn("Rate limit desactivado, Redis no responde: {}. Reintento en {}s",
                            ex.getMessage(), RECONNECT_DELAY.toSeconds());
                }
            }
            return proxyManager;
        }
    }

    private static Bandwidth limit(long tokens, Duration period) {
        return Bandwidth.builder().capacity(tokens).refillGreedy(tokens, period).build();
    }

    private static BucketConfiguration config(Bandwidth... limits) {
        var builder = BucketConfiguration.builder();
        for (Bandwidth limit : limits) {
            builder.addLimit(limit);
        }
        return builder.build();
    }

    private static byte[] bucketKey(RateLimitGroup group, String key) {
        return ("rl:" + group.name().toLowerCase(Locale.ROOT) + ":" + key).getBytes(StandardCharsets.UTF_8);
    }

    /** Redondeo hacia arriba y minimo 1: Retry-After: 0 haria que el cliente reintente al instante. */
    private static long toRetryAfterSeconds(long nanosToWait) {
        return Math.max(1, Duration.ofNanos(nanosToWait).plusMillis(999).toSeconds());
    }

    /** retryAfterSeconds solo tiene sentido si allowed es false. */
    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }
}
