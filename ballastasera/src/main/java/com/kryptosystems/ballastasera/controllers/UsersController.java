package com.kryptosystems.ballastasera.controllers;


import com.kryptosystems.ballastasera.models.dtos.CursorPageDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.dtos.UserAvatarUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.UserDto;
import com.kryptosystems.ballastasera.models.dtos.UserProfileDto;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.BlocksService;
import com.kryptosystems.ballastasera.services.manager.FollowsService;
import com.kryptosystems.ballastasera.services.manager.UserActivityService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private static final String MY_FOLLOWERS = "/me/followers";
    private static final String MY_FOLLOWING = "/me/following";
    private static final String MY_FRIENDS = "/me/friends";
    private static final String MY_BLOCKS = "/me/blocks";
    private static final String MY_AVATAR = "/me/avatar";
    private static final String MY_AVATAR_SOURCE = "/me/avatar/source";
    private static final String USER_PROFILE = "/{id}";
    private static final String USER_ATTENDANCE = "/{id}/attendance";
    private static final String FOLLOW = "/{id}/follow";
    private static final String BLOCK = "/{id}/block";

    private final UserActivityService userActivityService;
    private final UsersService usersService;
    private final FollowsService followsService;
    private final BlocksService blocksService;
    private final EventsMapper eventsMapper;
    private final UserMapper userMapper;

    /** Requiere estar autenticado. Perfil de un usuario (ej. desde la lista de "parteciperò"):
     * 404 si es privado, borrado, baneado o hay un bloqueo, salvo que sea el propio.
     * Suma la relacion con quien mira (boton Segui / Ricambia / Amici), los contadores, «Avete in comune»
     * y el pallino «Cerco qualcuno». */
    @GetMapping(USER_PROFILE)
    public ResponseEntity<UserProfileDto> getUserProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable UUID id) {
        var user = usersService.findPublicProfile(principal.getId(), id);
        var relation = followsService.findRelation(principal.getId(), id);
        var counts = followsService.countSocial(id);
        var activity = userActivityService.findProfileActivity(principal.getId(), user, relation);
        return ResponseEntity.ok(userMapper.toProfileDto(user, principal.getId(), relation, counts, activity));
    }

    /** Requiere estar autenticado. «Le sue prossime serate» (past=true: las pasadas), mismo orden y cursor que
     * /me/attendance. 404 si el perfil no se puede ver. Si eligio que quien mira no vea sus serate: lista vacia. */
    @GetMapping(USER_ATTENDANCE)
    public ResponseEntity<CursorPageDto<EventCardDto>> findUserAttendance(@AuthenticationPrincipal UserPrincipal principal,
                                                                         @PathVariable UUID id,
                                                                         @RequestParam(defaultValue = "false") boolean past,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursorStartAt,
                                                                         @RequestParam(required = false) UUID cursorId,
                                                                         @RequestParam(defaultValue = "20") int limit) {
        if (!userActivityService.canSeeAttendanceOf(principal.getId(), id)) {
            return ResponseEntity.ok(eventsMapper.toEventCardCursorPage(new SliceImpl<>(List.of()), 0));
        }
        var events = userActivityService.findMyAttendance(id, past, cursorStartAt, cursorId, limit);
        var total = userActivityService.countMyAttendance(id, past);
        return ResponseEntity.ok(eventsMapper.toEventCardCursorPage(events, total));
    }

    /** Requiere estar autenticado. Seguir. Idempotente. 400 si es uno mismo; 404 si el perfil no se puede ver. */
    @PostMapping(FOLLOW)
    public ResponseEntity<Void> follow(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        followsService.follow(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Dejar de seguir. Idempotente. */
    @DeleteMapping(FOLLOW)
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        followsService.unfollow(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Bloquear: borra los follows en los dos sentidos. Idempotente. */
    @PostMapping(BLOCK)
    public ResponseEntity<Void> block(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        blocksService.block(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Desbloquear. Idempotente. */
    @DeleteMapping(BLOCK)
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        blocksService.unblock(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Sube o cambia la foto propia (parte multipart "avatar", max. 5 MB,
     * JPG/PNG/WebP): se guarda en webp y pasa a ser la foto de perfil. 413 si pesa mas, 415 si no es una imagen. */
    @PatchMapping(value = MY_AVATAR, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDto> uploadMyAvatar(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestParam("avatar") MultipartFile file) {
        var user = usersService.uploadAvatar(principal.getId(), file);
        return ResponseEntity.ok(userMapper.toDto(user, followsService.countSocial(principal.getId())));
    }

    /** Requiere estar autenticado. «Usa la foto di Google» (GOOGLE) o «Nessuna foto» (NONE). */
    @PatchMapping(MY_AVATAR_SOURCE)
    public ResponseEntity<UserDto> changeMyAvatarSource(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody UserAvatarUpdateDto body) {
        var user = usersService.changeAvatarSource(principal.getId(), body.getSource());
        return ResponseEntity.ok(userMapper.toDto(user, followsService.countSocial(principal.getId())));
    }

    /** Requiere estar autenticado. Quienes me siguen, los mas recientes primero.
     * cursorStartAt/cursorId: fecha del follow e id de la ultima persona de la pagina anterior. */
    @GetMapping(MY_FOLLOWERS)
    public ResponseEntity<CursorPageDto<UserSummaryDto>> findMyFollowers(@AuthenticationPrincipal UserPrincipal principal,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursorStartAt,
                                                                        @RequestParam(required = false) UUID cursorId,
                                                                        @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(followsService.findFollowers(principal.getId(), cursorStartAt, cursorId, limit));
    }

    /** Requiere estar autenticado. A quienes sigo, los mas recientes primero. */
    @GetMapping(MY_FOLLOWING)
    public ResponseEntity<CursorPageDto<UserSummaryDto>> findMyFollowing(@AuthenticationPrincipal UserPrincipal principal,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursorStartAt,
                                                                        @RequestParam(required = false) UUID cursorId,
                                                                        @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(followsService.findFollowing(principal.getId(), cursorStartAt, cursorId, limit));
    }

    /** Requiere estar autenticado. Mis amigos (nos seguimos los dos). q busca por nombre. */
    @GetMapping(MY_FRIENDS)
    public ResponseEntity<CursorPageDto<UserSummaryDto>> findMyFriends(@AuthenticationPrincipal UserPrincipal principal,
                                                                      @RequestParam(required = false) @Size(max = 50) String q,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cursorStartAt,
                                                                      @RequestParam(required = false) UUID cursorId,
                                                                      @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(followsService.findFriends(principal.getId(), q, cursorStartAt, cursorId, limit));
    }

    /** Requiere estar autenticado. Personas que bloquee, las mas recientes primero. Sin paginar: son pocas. */
    @GetMapping(MY_BLOCKS)
    public ResponseEntity<List<UserSummaryDto>> findMyBlocks(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(blocksService.findBlocked(principal.getId()));
    }

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
