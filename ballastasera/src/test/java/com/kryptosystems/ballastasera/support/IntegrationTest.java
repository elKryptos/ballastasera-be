package com.kryptosystems.ballastasera.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base de los tests de integracion: la app entera (controller, service, repository) contra un Postgres y un
 * Redis reales en Docker, con las mismas imagenes que infra/docker-compose.yml. El esquema es
 * docs/ballastasera_schema.sql, el mismo de prod. Requiere Docker Desktop abierto.
 * <p>
 * Los contenedores arrancan una sola vez para todos los tests que heredan de aqui (se comparten entre
 * clases) y Testcontainers los borra al terminar. No tocan la DB de dev: todas las propiedades que vienen
 * del .env se pisan abajo.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-jwt-secret-key-with-at-least-32-characters",
        "app.jwt.expiration-ms=86400000",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "app.frontend.oauth2-redirect-uri=http://localhost:4200/oauth2/callback",
        "app.cors.allowed-origins=http://localhost:4200",
        "frontend.url=http://localhost:4200",
        "resend.api-key=test-api-key",
        "resend.from-email=test@example.com",
        "email.logo-url=http://localhost/logo.png",
        "storage.endpoint=http://localhost:9000",
        "storage.access-key=test",
        "storage.secret-key=test",
        "storage.raw-bucket=test-raw",
        "storage.flyers-bucket=test-flyers",
        "storage.logos-bucket=test-logos"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../docs/ballastasera_schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.6.6-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /** Cada test empieza sin buckets de rate limit ni cache de un test anterior. */
    @BeforeEach
    void flushRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
