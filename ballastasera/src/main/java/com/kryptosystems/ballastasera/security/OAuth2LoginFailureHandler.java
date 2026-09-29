package com.kryptosystems.ballastasera.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend.oauth2-redirect-uri}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String errorCode = exception instanceof OAuth2AuthenticationException oAuth2Exception
                ? oAuth2Exception.getError().getErrorCode()
                : "login_failed";

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUrl)
                .queryParam("error", errorCode)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
