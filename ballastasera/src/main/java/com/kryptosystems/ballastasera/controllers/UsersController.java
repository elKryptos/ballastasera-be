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

import static com.kryptosystems.ballastasera.utilities.RestConstants.USERS;

@RestController
@RequestMapping(USERS)
@RequiredArgsConstructor
public class UsersController {

    private static final String MY_FAVORITES = "/me/favorites";
    private static final String MY_ATTENDANCE = "/me/attendance";

    private final UserActivityService userActivityService;

    @GetMapping(MY_FAVORITES)
    public ResponseEntity<List<EventCardDto>> getMyFavorites(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userActivityService.getMyFavorites(principal.getId()));
    }

    @GetMapping(MY_ATTENDANCE)
    public ResponseEntity<List<AttendanceCardDto>> getMyAttendance(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userActivityService.getMyAttendance(principal.getId()));
    }

}
