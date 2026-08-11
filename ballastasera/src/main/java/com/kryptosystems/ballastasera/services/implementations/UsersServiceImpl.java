package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.utilities.InstagramUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private final UsersRepository usersRepository;

    @Override
    public List<Users> findAll() {
        return usersRepository.findAll();
    }

    @Override
    public Users findById(UUID id) {
        return usersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + id));
    }

    @Override
    public Users findByEmail(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email " + email));
    }

    @Override
    public Users findByGoogleId(String googleId) {
        return usersRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with googleId " + googleId));
    }

    @Override
    public Users save(Users user) {
        return usersRepository.save(user);
    }

    @Override
    public void deleteById(UUID id) {
        usersRepository.deleteById(id);
    }

    @Override
    public Users updateSocialProfile(UUID userId, String instagram, boolean showProfilePublic) {
        Users user = findById(userId);
        user.setInstagram(InstagramUtils.normalizeHandle(instagram));
        user.setShowProfilePublic(showProfilePublic);
        return usersRepository.save(user);
    }

    @Override
    public Users promoteToOrganizer(UUID userId){
        Users user = findById(userId);
        /** Usar == para comparar el user.getRole() es mas seguro, si en caso user.getRole() es null,
         *  entonces da false, mientras que al usar equals podria lanzar una NPE */
        if (user.getRole() == UserRole.USER){
            user.setRole(UserRole.ORGANIZER);
        }
        return usersRepository.save(user);
    }
}
