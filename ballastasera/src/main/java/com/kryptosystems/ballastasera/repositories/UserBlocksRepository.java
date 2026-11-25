package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.UserBlocks;
import com.kryptosystems.ballastasera.models.entities.keys.UserBlockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBlocksRepository extends JpaRepository<UserBlocks, UserBlockId> {

    /** El unico chequeo de bloqueo: vale en los dos sentidos (si Ana bloquea a Luca, ninguno ve al otro). */
    @Query("""
            SELECT COUNT(b) > 0 FROM UserBlocks b
            WHERE (b.id.blockerId = :userA AND b.id.blockedId = :userB)
               OR (b.id.blockerId = :userB AND b.id.blockedId = :userA)
            """)
    boolean existsBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** «Utenti bloccati» en la configuracion: los mas recientes primero. */
    @Query("SELECT b FROM UserBlocks b JOIN FETCH b.blocked WHERE b.id.blockerId = :blockerId ORDER BY b.createdAt DESC")
    List<UserBlocks> findByBlockerIdWithBlocked(@Param("blockerId") UUID blockerId);
}
