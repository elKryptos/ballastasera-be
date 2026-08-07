package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsersService usersService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userMapper.toDto(usersService.findById(principal.getId())));
    }

    /** Perfil social: handle de Instagram + si se muestra publicamente en "quien va". */
    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserUpdateDto body
    ) {
        var user = usersService.updateSocialProfile(
                principal.getId(), body.getInstagram(), body.getShowProfilePublic());
        return ResponseEntity.ok(userMapper.toDto(user));
    }
}