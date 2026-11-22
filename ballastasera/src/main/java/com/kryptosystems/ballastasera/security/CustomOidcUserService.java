package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.enums.AvatarSource;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
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
                .or(() -> usersRepository.findByEmail(email)
                                .map(existing -> {
                                    if (existing.getGoogleId() != null && !existing.getGoogleId().equals(googleId)) {
                                        log.warn("googleId cambiato per email={}: {} -> {}", email, existing.getGoogleId(), googleId);
                                    }
                                    return existing;
                                }))
                .orElse(new Users());

        if (user.isDeleted()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_deleted"));
        }
        if (user.isBanned()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_banned"));
        }
        user.setGoogleId(googleId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        applyGoogleAvatar(user, avatarUrl);
        user.setLoginCount(user.getLoginCount() + 1);
        user.setLastLogin(OffsetDateTime.now());

        Users saved = usersRepository.save(user);

        return new CustomOidcUser(oidcUser, saved);
    }

    /**
     * La foto de Google se guarda siempre (cambia si el usuario la cambia en Google), pero solo se muestra si
     * eligio la de Google. Antes se pisaba avatarUrl en cada login y una foto subida se perdia.
     */
    static void applyGoogleAvatar(Users user, String googleAvatarUrl) {
        user.setGoogleAvatarUrl(googleAvatarUrl);
        if (user.getAvatarSource() == AvatarSource.GOOGLE) {
            user.setAvatarUrl(googleAvatarUrl);
        }
    }
}