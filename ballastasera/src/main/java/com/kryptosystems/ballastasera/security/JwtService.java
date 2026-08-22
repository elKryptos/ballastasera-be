package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.entities.RevokedTokens;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.RevokedTokensRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final RevokedTokensRepository revokedTokensRepository;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs,
                       RevokedTokensRepository revokedTokensRepository) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.revokedTokensRepository = revokedTokensRepository;
    }

    public String generateToken(Users user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UserRole extractRole(Claims claims) {
        return UserRole.valueOf(claims.get("role", String.class));
    }

    public UUID extractJti(Claims claims) {
        return UUID.fromString(claims.getId());
    }

    public Instant extractExpiration(Claims claims) {
        return claims.getExpiration().toInstant();
    }

    public void revokeToken(Claims claims) {
        RevokedTokens revoked = new RevokedTokens();
        revoked.setJti(extractJti(claims));
        revoked.setExpiresAt(extractExpiration(claims).atOffset(ZoneOffset.UTC));
        revokedTokensRepository.save(revoked);
    }
}