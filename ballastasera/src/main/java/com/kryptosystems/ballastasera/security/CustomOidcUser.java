package com.kryptosystems.ballastasera.security;

import com.kryptosystems.ballastasera.models.entities.Users;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomOidcUser implements OidcUser {

    private final OidcUser delegate;
    private final UUID id;
    private final Collection<GrantedAuthority> authorities;

    public CustomOidcUser(OidcUser delegate, Users user) {
        this.delegate = delegate;
        this.id = user.getId();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}