package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.exceptions.core.ErrorDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Limita las requests por usuario (con login) o por IP (sin login), por grupo (RateLimitGroup). Pasado el
 * limite responde 429 con Retry-After y el mismo cuerpo que los otros errores (code RATE_LIMITED).
 * <p>
 * Va en la cadena de Security despues de JwtAuthenticationFilter, para saber quien es el usuario. No es un
 * @Component: Spring Boot lo registraria tambien como filtro del servlet y todos los @WebMvcTest tendrian que
 * mockearlo. Lo crea SecurityConfig.
 * <p>
 * La IP es getRemoteAddr(): con server.forward-headers-strategy=framework ya es la del cliente detras de nginx.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMITED = "RATE_LIMITED";

    private static final String HEALTH_PATH = "/rest/health";
    private static final List<String> AUTH_PREFIXES = List.of("/oauth2/", "/login/");
    /** Acciones sociales (fases 1, 4 y 6 de social-plan.md); solo cuentan los metodos que escriben. */
    private static final List<PathPattern> SOCIAL_PATTERNS = parse(
            "/rest/users/{id}/follow",
            "/rest/users/{id}/block",
            "/rest/users/{id}/report",
            "/rest/users/{id}/invites");

    private final RateLimitService rateLimitService;
    private final JsonMapper jsonMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimitService.isEnabled()
                || HttpMethod.OPTIONS.matches(request.getMethod())  // preflight de CORS
                || HEALTH_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        RateLimitGroup group = resolveGroup(request);
        String key = resolveKey(group, request);
        var result = rateLimitService.tryConsume(group, key);

        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit {} superado por {} en {} {}", group, key, request.getMethod(), request.getRequestURI());
        writeTooManyRequests(request, response, result.retryAfterSeconds());
    }

    static RateLimitGroup resolveGroup(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (AUTH_PREFIXES.stream().anyMatch(path::startsWith)) {
            return RateLimitGroup.AUTH;
        }

        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)) {
            return RateLimitGroup.READ;
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return RateLimitGroup.UPLOAD;
        }

        PathContainer pathContainer = PathContainer.parsePath(path);
        if (SOCIAL_PATTERNS.stream().anyMatch(pattern -> pattern.matches(pathContainer))) {
            return RateLimitGroup.SOCIAL;
        }
        return RateLimitGroup.WRITE;
    }

    /** El login siempre va por IP: en /oauth2 todavia no hay usuario (y si lo hay, no cuenta). */
    private static String resolveKey(RateLimitGroup group, HttpServletRequest request) {
        if (group != RateLimitGroup.AUTH) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                return "user:" + principal.getId();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Too many requests",
                request.getRequestURI(),
                "Rate limit exceeded, retry in " + retryAfterSeconds + " seconds",
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of("code", RATE_LIMITED)
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), errorDetails);
    }

    private static List<PathPattern> parse(String... patterns) {
        return Arrays.stream(patterns)
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
    }
}
