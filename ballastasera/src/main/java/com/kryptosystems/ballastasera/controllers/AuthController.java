package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsersService usersService;

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal UserPrincipal principal) {
        Users user = usersService.findById(principal.getId());

        Map<String, String> response = new HashMap<>();
        response.put("userId", user.getId().toString());
        response.put("email", user.getEmail());
        response.put("displayName", user.getDisplayName());
        response.put("role", user.getRole().name());
        response.put("avatarUrl", user.getAvatarUrl());
        return response;
    }
}
