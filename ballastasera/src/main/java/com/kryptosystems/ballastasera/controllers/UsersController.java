package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.AttendanceCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserActivityService userActivityService;

    @GetMapping("/me/favorites")
    public ResponseEntity<List<EventCardDto>> getMyFavorites(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userActivityService.getMyFavorites(principal.getId()));
    }

    @GetMapping("/me/attendance")
    public ResponseEntity<List<AttendanceCardDto>> getMyAttendance(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userActivityService.getMyAttendance(principal.getId()));
    }

}
