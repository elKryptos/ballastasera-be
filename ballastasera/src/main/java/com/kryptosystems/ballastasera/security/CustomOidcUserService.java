package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UsersRepository usersRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String googleId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String displayName = oidcUser.getFullName() != null ? oidcUser.getFullName() : email;
        String avatarUrl = oidcUser.getPicture();

        Users user = usersRepository.findByGoogleId(googleId)
                .or(() -> usersRepository.findByEmail(email))
                .orElseGet(Users::new);

        user.setGoogleId(googleId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);
        if (user.getId() == null) {
            user.setActive(true);
        }

        Users saved = usersRepository.save(user);

        return new CustomOidcUser(oidcUser, saved);
    }
}