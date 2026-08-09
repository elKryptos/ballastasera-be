package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerDetailDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerSummaryDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.mappers.EventsMapper;
import com.kryptosystems.ballastasera.security.UserPrincipal;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizers")
@RequiredArgsConstructor
public class OrganizersController {

    private final OrganizersService organizersService;
    private final EventsMapper eventsMapper;

    /** Requiere estar autenticado. Crea el perfil de organizador. Queda pendiente (isVerified=false) hasta
     * que un admin lo apruebe; solo entonces se pueden publicar eventos. */
    @PostMapping()
    public ResponseEntity<OrganizerDetailDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                     @Valid @RequestBody OrganizerCreateDto body
    ) {
        var organizer = organizersService.createForUser(principal.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventsMapper.toOrganizerDetail(organizer));
    }

    /** Público, nadie necesita loguearse. Sirve para la página de perfil del organizador
     * que ve cualquier visitante (como el detalle de un evento).   */
    @GetMapping("/{slug}")
    public ResponseEntity<OrganizerDetailDto> getBySlug(@PathVariable String slug) {
        var organizer = organizersService.findBySlug(slug);
        return ResponseEntity.ok(eventsMapper.toOrganizerDetail(organizer));
    }

    /** Requiere estar autenticado. El mismo usuario. Para que el dueño vea su(s) propio(s)
     * perfil(es) de organizador, típicamente al entrar a su panel/dashboard.    */
    @GetMapping("/me")
    public ResponseEntity<List<OrganizerDetailDto>> getMyOrganizers(@AuthenticationPrincipal UserPrincipal principal) {
        var organizers = organizersService.findByUserId(principal.getId());
        return ResponseEntity.ok(organizers.stream().map(eventsMapper::toOrganizerDetail).toList());
    }

    /** Requiere estar autenticado. Editar el propio perfil de organizador. No estoy actulizando
     * el Slug ya creado para no romper los link ya compartidos, necesario poner un mensaje en
     * el FE para avisar de la decision tomada en el BE */
    @PatchMapping("/{id}")
    public ResponseEntity<OrganizerDetailDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody OrganizerUpdateDto body) {
        var organizer = organizersService.update(id, principal.getId(), body);
        return ResponseEntity.ok(eventsMapper.toOrganizerDetail(organizer));
    }

    /** Público, directorio general (como listar eventos), solo muestra los ya verificados
     * para no exponer perfiles pendientes de aprobación.  */
    @GetMapping("")
    public ResponseEntity<Page<OrganizerSummaryDto>> getOrganizersList(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        var organizers = organizersService.findVerified(PageRequest.of(page, size))
                .map(eventsMapper::toOrganizerSummary);
        return ResponseEntity.ok(organizers);
    }

}
