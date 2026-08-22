package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.security.JwtService;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.kryptosystems.ballastasera.utilities.RestConstants.AUTH;

@RestController
@RequestMapping(AUTH)
@RequiredArgsConstructor
public class AuthController {

    private static final String ME = "/me";
    private static final String LOGOUT = "/logout";
    private static final String BEARER_PREFIX = "Bearer ";

    private final UsersService usersService;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @GetMapping(ME)
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userMapper.toDto(usersService.findById(principal.getId())));
    }

    /** Perfil social: handle de Instagram + si se muestra publicamente en "quien va". */
    @PatchMapping(ME)
    public ResponseEntity<UserDto> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserUpdateDto body
    ) {
        var user = usersService.updateSocialProfile(
                principal.getId(), body.getInstagram(), body.getShowProfilePublic());
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    /** Requiere estar autenticado. Revoca el JWT actual: no vuelve a ser aceptado aunque no haya expirado. */
    @PostMapping(LOGOUT)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            Claims claims = jwtService.parseClaims(header.substring(BEARER_PREFIX.length()));
            jwtService.revokeToken(claims);
        }
        return ResponseEntity.noContent().build();
    }
}