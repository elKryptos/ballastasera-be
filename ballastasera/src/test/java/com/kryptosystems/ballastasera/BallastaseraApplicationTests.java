package com.kryptosystems.ballastasera;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.jwt.secret=test-jwt-secret-key-with-at-least-32-characters",
        "app.jwt.expiration-ms=86400000",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "app.frontend.oauth2-redirect-uri=http://localhost:4200/oauth2/callback",
        "resend.api-key=test-api-key",
        "resend.from-email=test@example.com",
        "frontend.url=http://localhost:4200"
})
class BallastaseraApplicationTests {

    @Test
    void contextLoads() {
    }

}
