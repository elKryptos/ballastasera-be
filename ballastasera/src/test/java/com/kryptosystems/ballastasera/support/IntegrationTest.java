package com.kryptosystems.ballastasera.support;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.security.JwtService;
import com.kryptosystems.ballastasera.services.manager.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.UUID;

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
    protected JdbcTemplate jdbcTemplate;

    /** Ningun test manda emails de verdad (Resend). Los que los esperan lo comprueban con verify. Mockito lo resetea
     * despues de cada test. */
    @MockitoBean
    protected EmailService emailService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private JwtService jwtService;

    /**
     * Cada test empieza sin buckets de rate limit ni cache, y sin usuarios de un test anterior. TRUNCATE ... CASCADE
     * vacia tambien todo lo que cuelga de users (follows, bloqueos, organizers, eventos...). Ciudades y estilos
     * de baile (el seed del esquema) se quedan.
     */
    @BeforeEach
    void cleanState() {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        jdbcTemplate.execute("TRUNCATE users, events CASCADE");
    }

    /** Usuario con perfil publico, como recien llegado del login con Google. */
    protected Users createUser(String displayName) {
        Users user = new Users();
        user.setEmail(displayName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName(displayName);
        return usersRepository.save(user);
    }

    protected Users save(Users user) {
        return usersRepository.save(user);
    }

    /** La request va con el JWT de user, como si hubiera hecho login. */
    protected RequestPostProcessor as(Users user) {
        String token = jwtService.generateToken(user);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /** Serata publicada de mañana en la primera ciudad del seed. Devuelve su id. */
    protected UUID createEvent(String title) {
        return createEventStartingIn(title, "1 day");
    }

    /**
     * Serata publicada que empieza dentro de startsIn, un intervalo de Postgres: "2 hours" (cercana),
     * "-3 days" (ya terminada: sin end_at dura 4 horas). Devuelve su id.
     */
    protected UUID createEventStartingIn(String title, String startsIn) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO events (city_id, title, slug, start_at, address, latitude, longitude, status)
                VALUES ((SELECT id FROM cities ORDER BY id LIMIT 1), ?, ?, now() + CAST(? AS interval),
                        'Via Roma 1', 45.46, 9.19, 'PUBLISHED')
                RETURNING id
                """, UUID.class, title, "test-" + UUID.randomUUID(), startsIn);
    }

    /** Organizer verificado del tipo dado (PERSON, VENUE, CLUB, SCHOOL). Devuelve su id. */
    protected UUID createOrganizer(String name, String type) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO organizers (name, slug, type, is_verified)
                VALUES (?, ?, CAST(? AS organizer_type), TRUE)
                RETURNING id
                """, UUID.class, name, "test-" + UUID.randomUUID(), type);
    }

    /** «Ci vado» directo en la DB, sin pasar por el endpoint. */
    protected void attend(Users user, UUID eventId) {
        jdbcTemplate.update("INSERT INTO event_attendance (user_id, event_id) VALUES (?, ?)", user.getId(), eventId);
    }
}
