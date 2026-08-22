package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.RevokedTokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RevokedTokensRepository extends JpaRepository<RevokedTokens, UUID> {

    boolean existsByJti(UUID jti);

    @Modifying
    @Query("delete from RevokedTokens t where t.expiresAt < :now")
    void deleteAllExpiredBefore(OffsetDateTime now);
}