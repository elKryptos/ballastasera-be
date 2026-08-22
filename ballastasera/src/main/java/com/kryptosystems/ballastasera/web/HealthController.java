package com.kryptosystems.ballastasera.web;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.kryptosystems.ballastasera.utilities.RestConstants.HEALTH;

@RestController
public class HealthController {

    @GetMapping(HEALTH)
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "ballastasera",
                "time", OffsetDateTime.now().toString());
    }
}
