package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.entities.Users;

import java.util.List;
import java.util.UUID;

public interface UsersService {
    List<Users> findAll();
    Users findById(UUID id);
    Users findByEmail(String email);
    Users findByGoogleId(String googleId);
    Users save(Users user);
    void deleteById(UUID id);

    /** Actualiza el perfil social (Instagram + visibilidad publica) del usuario. */
    Users updateSocialProfile(UUID userId, String instagram, boolean showProfilePublic);
}
