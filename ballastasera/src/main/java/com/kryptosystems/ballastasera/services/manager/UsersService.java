package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.enums.AvatarSource;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Users;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UsersService {
    List<Users> findAll();
    Users findById(UUID id);
    Users findByIdWithDanceStyles(UUID id);
    Users findByEmail(String email);
    Users findByGoogleId(String googleId);
    Users save(Users user);
    void deleteById(UUID id);

    /** Reemplaza el perfil completo (datos personales + estilos con nivel) del usuario. */
    Users updateProfile(UUID userId, UserUpdateDto dto);

    /** Sube una foto propia (max. 5 MB, JPG/PNG/WebP) y la pone como foto de perfil. */
    Users uploadAvatar(UUID userId, MultipartFile file);

    /** Vuelve a la foto de Google (GOOGLE) o se queda sin foto (NONE). Borra la propia si habia. */
    Users changeAvatarSource(UUID userId, AvatarSource source);

    /** Perfil de otro usuario: 404 si es privado, borrado o baneado, salvo que sea el propio. */
    Users findPublicProfile(UUID viewerId, UUID userId);

    /** Sube el rol a ORGANIZER si el usuario todavia es USER (no baja a un ADMIN). */
    Users promoteToOrganizer(UUID userId);

    Users demoteToUser(UUID userId);
}
