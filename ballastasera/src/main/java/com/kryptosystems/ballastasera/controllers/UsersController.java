package com.kryptosystems.ballastasera.controllers;


import com.kryptosystems.ballastasera.models.dtos.CursorPageDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.UserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.kryptosystems.ballastasera.utilities.RestConstants.USERS;

@RestController
@RequestMapping(USERS)
@RequiredArgsConstructor
public class UsersController {

    private static final String MY_FAVORITES_IDS = "/me/favorites/ids";
    private static final String MY_FAVORITES = "/me/favorites";
    private static final String MY_ATTENDANCE = "/me/attendance";

    private final UserActivityService userActivityService;
    private final EventsMapper eventsMapper;

    /** Requiere estar autenticado. Solo los ids de los eventos con like: lo usa
     * el Frontend para pintar los corazones de /lista en una sola request */
    @GetMapping(MY_FAVORITES_IDS)
    public ResponseEntity<List<UUID>> getMyFavoriteIds(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userActivityService.findMyFavoritesEventsIds(principal.getId()));
    }

    @GetMapping(MY_FAVORITES)
    public ResponseEntity<CursorPageDto<EventCardDto>> findMyFavorites(@AuthenticationPrincipal UserPrincipal principal,
                                                                      @RequestParam(defaultValue = "false") boolean past,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursorStartAt,
                                                                      @RequestParam(required = false) UUID cursorId,
                                                                      @RequestParam(defaultValue = "20") int limit) {
        var events = userActivityService.findMyFavorites(principal.getId(), past, cursorStartAt, cursorId, limit);
        long total = userActivityService.countMyFavorites(principal.getId(), past);
        return ResponseEntity.ok(eventsMapper.toEventCardCursorPage(events, total));
    }

    @GetMapping(MY_ATTENDANCE)
    public ResponseEntity<CursorPageDto<EventCardDto>> findMyAttendance(@AuthenticationPrincipal UserPrincipal principal,
                                                                   @RequestParam(defaultValue = "false") boolean past,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursorStartAt,
                                                                   @RequestParam(required = false) UUID cursorId,
                                                                   @RequestParam(defaultValue = "20") int limit) {
        var events = userActivityService.findMyAttendance(principal.getId(), past, cursorStartAt, cursorId, limit);
        var total = userActivityService.countMyAttendance(principal.getId(), past);
        return ResponseEntity.ok(eventsMapper.toEventCardCursorPage(events, total));
    }

}
