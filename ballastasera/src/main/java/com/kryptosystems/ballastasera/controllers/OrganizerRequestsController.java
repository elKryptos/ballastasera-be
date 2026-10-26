package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestDto;
import com.kryptosystems.ballastasera.models.mappers.OrganizerRequestMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.OrganizerRequestsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.kryptosystems.ballastasera.utilities.RestConstants.ORGANIZER_REQUESTS;

@RestController
@RequestMapping(ORGANIZER_REQUESTS)
@RequiredArgsConstructor
public class OrganizerRequestsController {

    private static final String CREATE = "";
    private static final String GET_MY_LATEST = "/me";
    private static final String CANCEL = "/{id}";
    private static final String ACKNOWLEDGE = "/{id}/acknowledge";

    private final OrganizerRequestsService organizerRequestsService;
    private final OrganizerRequestMapper organizerRequestMapper;

    /** Requiere estar autenticado. Último paso del wizard «Diventa organizzatore»: crea la solicitud NEW
     * (perfil nuevo) o CLAIM (página que ya existe) en PENDING, hasta que un admin la revise. */
    @PostMapping(CREATE)
    public ResponseEntity<OrganizerRequestDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody OrganizerRequestCreateDto body) {
        var request = organizerRequestsService.create(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(organizerRequestMapper.toOrganizerRequestDto(request));
    }

    /** Requiere estar autenticado. El mismo usuario. Estado de su solicitud más reciente: el frontend decide
     * si enseña el wizard, «in attesa» o el resultado. 204 si nunca pidió. */
    @GetMapping(GET_MY_LATEST)
    public ResponseEntity<OrganizerRequestDto> getMyLatest(@AuthenticationPrincipal UserPrincipal principal) {
        return organizerRequestsService.findMyLatest(principal.getId())
                .map(organizerRequestMapper::toOrganizerRequestDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Requiere estar autenticado. Solo el dueño. Retira su solicitud mientras está PENDING (pasa a CANCELLED). */
    @DeleteMapping(CANCEL)
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        organizerRequestsService.cancel(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Requiere estar autenticado. Solo el dueño. El usuario ha visto el resultado (aprobada o rechazada):
     * el frontend deja de enseñar el aviso. */
    @PatchMapping(ACKNOWLEDGE)
    public ResponseEntity<OrganizerRequestDto> acknowledge(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID id) {
        var request = organizerRequestsService.acknowledge(id, principal.getId());
        return ResponseEntity.ok(organizerRequestMapper.toOrganizerRequestDto(request));
    }
}