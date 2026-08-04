package com.kryptosystems.ballastasera.repositories;

import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByGoogleId(String googleId);
}
