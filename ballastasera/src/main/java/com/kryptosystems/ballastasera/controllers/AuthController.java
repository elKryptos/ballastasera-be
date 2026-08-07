package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsersService usersService;

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal UserPrincipal principal) {
        return toResponse(usersService.findById(principal.getId()));
    }

    /** Perfil social: handle de Instagram + si se muestra publicamente en "quien va". */
    @PatchMapping("/me")
    public Map<String, String> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserUpdateDto body
    ) {
        Users user = usersService.findById(principal.getId());
        user.setInstagram(normalizeInstagram(body.getInstagram()));
        user.setShowProfilePublic(body.getShowProfilePublic());
        return toResponse(usersService.save(user));
    }

    private String normalizeInstagram(String instagram) {
        if (instagram == null || instagram.isBlank()) {
            return null;
        }
        String trimmed = instagram.trim();
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }

    private Map<String, String> toResponse(Users user) {
        Map<String, String> response = new HashMap<>();
        response.put("userId", user.getId().toString());
        response.put("email", user.getEmail());
        response.put("displayName", user.getDisplayName());
        response.put("role", user.getRole().name());
        response.put("avatarUrl", user.getAvatarUrl());
        response.put("instagram", user.getInstagram());
        response.put("showProfilePublic", String.valueOf(user.isShowProfilePublic()));
        return response;
    }
}
