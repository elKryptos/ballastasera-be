package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;
import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.events.OrganizerRequestReviewedEvent;
import com.kryptosystems.ballastasera.exceptions.OrganizerAlreadyClaimedException;
import com.kryptosystems.ballastasera.exceptions.OrganizerRequestConflictException;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestApproveDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestRejectDto;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.Venues;
import com.kryptosystems.ballastasera.models.mappers.OrganizerRequestMapper;
import com.kryptosystems.ballastasera.repositories.OrganizerRequestsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.VenuesRepository;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import com.kryptosystems.ballastasera.services.manager.OrganizerRequestsService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.utilities.InstagramUtils;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.kryptosystems.ballastasera.exceptions.OrganizerRequestConflictException.*;

@Service
@RequiredArgsConstructor
public class OrganizerRequestsServiceImpl implements OrganizerRequestsService {

    /** D3: un organizador por usuario. Regla del servicio, se puede subir sin migrar. */
    private static final int MAX_ORGANIZERS_PER_USER = 1;
    /** Índice parcial que garantiza una sola PENDING por usuario aunque dos create lleguen a la vez. */
    private static final String ONE_PENDING_CONSTRAINT = "uq_org_requests_one_pending";
    /** Mensaje del rechazo automático de las CLAIM que pierden el organizador. En italiano dice «pagina»
     * porque es lo que ve el usuario en el wizard («Rivendica una pagina esistente»). */
    private static final String PAGE_ASSIGNED_TO_OTHER = "La pagina è stata assegnata a un altro utente.";

    private final OrganizerRequestsRepository organizerRequestsRepository;
    private final OrganizersRepository organizersRepository;
    private final VenuesRepository venuesRepository;
    private final OrganizersService organizersService;
    private final UsersService usersService;
    private final CitiesService citiesService;
    private final OrganizerRequestMapper organizerRequestMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrganizerRequests create(UUID userId, OrganizerRequestCreateDto dto) {
        Users user = usersService.findById(userId);
        if (user.isBanned()) {
            throw new AccessDeniedException("Banned users cannot request an organizer profile");
        }
        if (organizerRequestsRepository.existsByUserIdAndStatus(userId, OrganizerRequestStatus.PENDING)) {
            throw new OrganizerRequestConflictException(PENDING_REQUEST_EXISTS, "User already has a pending request");
        }
        // Un ADMIN también puede pedir: la regla es la misma
        if (organizersRepository.countByUserId(userId) >= MAX_ORGANIZERS_PER_USER) {
            throw new OrganizerRequestConflictException(ORGANIZER_LIMIT_REACHED, "User already has the maximum number of organizers");
        }

        OrganizerRequests request = organizerRequestMapper.toOrganizerRequestEntity(dto);
        request.setUser(user);
        request.setInstagram(InstagramUtils.normalizeHandle(request.getInstagram()));
        request.setTermsAcceptedAt(OffsetDateTime.now());

        if (dto.getType() == OrganizerRequestType.CLAIM) {
            request.setOrganizer(findClaimTarget(dto.getTargetOrganizerId()));
            // D6: en CLAIM solo cuentan los datos de prueba; el perfil y el local ya son los del organizador pedido
            request.setName(null);
            request.setOrganizerType(null);
            request.setDescription(null);
            request.setWebsite(null);
            request.setFacebook(null);
            request.setVenueAddress(null);
        } else if (dto.getVenueCityId() != null) {
            // D11: las reglas de tipo ya las aplicó @ValidOrganizerRequest; aquí solo que la ciudad exista (404)
            request.setVenueCity(citiesService.findById(dto.getVenueCityId()));
        }
        // TODO 5.3: al reenviar una NEW, copiar el logoUrl de la REJECTED anterior cuando vuelva el logo de la solicitud

        try {
            // saveAndFlush: el INSERT tiene que salir aquí para capturar la violación del índice
            return organizerRequestsRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException ex) {
            if (isOnePendingViolation(ex)) {
                throw new OrganizerRequestConflictException(PENDING_REQUEST_EXISTS, "User already has a pending request");
            }
            throw ex;
        }
    }

    @Override
    public Optional<OrganizerRequests> findMyLatest(UUID userId) {
        return organizerRequestsRepository.findLatestByUserIdWithDetails(userId);
    }

    @Override
    @Transactional
    public void cancel(UUID requestId, UUID userId) {
        OrganizerRequests request = organizerRequestsRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Organizer request not found with id " + requestId));
        assertOwnership(request, userId);
        if (request.getStatus() != OrganizerRequestStatus.PENDING) {
            throw new OrganizerRequestConflictException(REQUEST_NOT_PENDING, "Only pending requests can be cancelled");
        }
        request.setStatus(OrganizerRequestStatus.CANCELLED);
        // La canceló el propio usuario: no hay resultado que enseñarle en GET /me
        request.setAcknowledgedAt(OffsetDateTime.now());
    }

    @Override
    @Transactional
    public OrganizerRequests acknowledge(UUID requestId, UUID userId) {
        // WithDetails: el controller la mapea a OrganizerRequestDto (organizer, venue, venueCity)
        OrganizerRequests request = organizerRequestsRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Organizer request not found with id " + requestId));
        assertOwnership(request, userId);
        if (request.getStatus() == OrganizerRequestStatus.PENDING) {
            throw new OrganizerRequestConflictException(REQUEST_STILL_PENDING, "Pending requests have no result to acknowledge");
        }
        // Idempotente: la primera fecha se queda
        if (request.getAcknowledgedAt() == null) {
            request.setAcknowledgedAt(OffsetDateTime.now());
        }
        return request;
    }

    @Override
    public Page<OrganizerRequests> findByStatus(OrganizerRequestStatus status, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortFor(status));
        return organizerRequestsRepository.findByStatusWithDetails(status, sorted);
    }

    @Override
    public OrganizerRequests findById(UUID requestId) {
        return organizerRequestsRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Organizer request not found with id " + requestId));
    }

    @Override
    public long countPreviousRejections(OrganizerRequests request) {
        long rejections = organizerRequestsRepository.countByUserIdAndStatus(request.getUser().getId(), OrganizerRequestStatus.REJECTED);
        return request.getStatus() == OrganizerRequestStatus.REJECTED ? rejections - 1 : rejections;
    }

    /** Si algo lanza (409, 404), la transacción hace rollback: no queda organizador creado, no cambia el rol
     * y los eventos de email se descartan. El CacheEvict tampoco se ejecuta si el método lanza. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.VENUE_BY_CITY,
            RedisCacheConfig.VENUE_MAP_BY_CITY, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public OrganizerRequests approve(UUID requestId, UUID adminId, OrganizerRequestApproveDto dto) {
        OrganizerRequests request = findPending(requestId);
        Users user = request.getUser();
        // Solo pasa si mientras tanto el admin le asignó un organizador por la vía directa: hay que rechazarla
        if (organizersRepository.countByUserId(user.getId()) >= MAX_ORGANIZERS_PER_USER) {
            throw new OrganizerRequestConflictException(ORGANIZER_LIMIT_REACHED, "User already has the maximum number of organizers");
        }
        Users admin = usersService.findById(adminId);

        Organizers organizer = request.getType() == OrganizerRequestType.NEW
                ? createOrganizer(request)
                : claimOrganizer(request, admin);

        if (dto != null && dto.getVenueId() != null) {
            request.setVenue(linkVenue(dto.getVenueId(), organizer));
        }
        // D11: en una CLAIM solo falla si el admin creó el organizador sin su venue: enlazarlo y volver a aprobar
        if (organizer.getType().requiresVenue() && venuesRepository.findByOrganizerId(organizer.getId()).isEmpty()) {
            throw new OrganizerRequestConflictException(VENUE_REQUIRED, "Organizer type " + organizer.getType() + " requires a venue");
        }

        request.setStatus(OrganizerRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(OffsetDateTime.now());
        return request;
    }

    /** Sin @CacheEvict: rechazar no crea ni cambia organizadores ni venues. */
    @Override
    @Transactional
    public OrganizerRequests reject(UUID requestId, UUID adminId, OrganizerRequestRejectDto dto) {
        OrganizerRequests request = findPending(requestId);
        // "" o solo espacios cuentan como sin mensaje
        String message = dto.getMessage() == null || dto.getMessage().isBlank() ? null : dto.getMessage().trim();
        request.setStatus(OrganizerRequestStatus.REJECTED);
        request.setRejectionReason(dto.getReason());
        request.setRejectionMessage(message);
        request.setReviewedBy(usersService.findById(adminId));
        request.setReviewedAt(OffsetDateTime.now());

        // NEW: el nombre que pidió - CLAIM: el del organizador que reclama
        String organizerName = request.getType() == OrganizerRequestType.NEW
                ? request.getName()
                : request.getOrganizer().getName();
        eventPublisher.publishEvent(OrganizerRequestReviewedEvent.rejected(request.getUser().getEmail(), organizerName, dto.getReason(), message));
        return request;
    }

    /** approve y reject: con detalles (el controller la mapea a OrganizerRequestAdminDto) y 409 si ya no está PENDING. */
    private OrganizerRequests findPending(UUID requestId) {
        OrganizerRequests request = findById(requestId);
        if (request.getStatus() != OrganizerRequestStatus.PENDING) {
            throw new OrganizerRequestConflictException(REQUEST_NOT_PENDING, "Only pending requests can be reviewed");
        }
        return request;
    }

    /** NEW: organizador verificado, con dueño y slug único. El email de aprobación sale después del commit. */
    private Organizers createOrganizer(OrganizerRequests request) {
        Organizers organizer = organizerRequestMapper.toOrganizerEntity(request);
        organizer.setSlug(SlugUtils.uniqueSlug(request.getName(),
                slug -> organizersRepository.findBySlug(slug).isPresent()));
        organizer.setVerified(true);
        organizer.setClaimed(true);
        organizer.setUser(request.getUser());
        Organizers saved = organizersRepository.save(organizer);
        request.setOrganizer(saved);
        usersService.promoteToOrganizer(request.getUser().getId());
        eventPublisher.publishEvent(OrganizerRequestReviewedEvent.approved(request.getUser().getEmail(), saved.getName()));
        return saved;
    }

    /** CLAIM: claim() asigna el organizador, sube el rol y publica su propio email (409 si ya tiene dueño).
     * Las otras CLAIM PENDING del mismo organizador se rechazan como DUPLICATE, cada una con su email. */
    private Organizers claimOrganizer(OrganizerRequests request, Users admin) {
        Organizers target = organizersService.claim(request.getOrganizer().getId(), request.getUser().getId());
        List<OrganizerRequests> competing = organizerRequestsRepository
                .findByOrganizerIdAndTypeAndStatus(target.getId(), OrganizerRequestType.CLAIM, OrganizerRequestStatus.PENDING);
        for (OrganizerRequests other : competing) {
            // La propia solicitud sigue PENDING en este punto: sale en la lista
            if (other.getId().equals(request.getId())) {
                continue;
            }
            other.setStatus(OrganizerRequestStatus.REJECTED);
            other.setRejectionReason(OrganizerRejectReason.DUPLICATE);
            other.setRejectionMessage(PAGE_ASSIGNED_TO_OTHER);
            other.setReviewedBy(admin);
            other.setReviewedAt(OffsetDateTime.now());
            eventPublisher.publishEvent(OrganizerRequestReviewedEvent.rejected(other.getUser().getEmail(),
                    target.getName(), OrganizerRejectReason.DUPLICATE, PAGE_ASSIGNED_TO_OTHER));
        }
        return target;
    }

    /** D11: enlaza el venue que el admin acaba de crear. linkOrganizerIfFree es un UPDATE condicional: dos
     * approve a la vez no pueden quedarse el mismo venue. Si no cambia ninguna fila, el venue no existe (404)
     * o ya tiene organizador (409). */
    private Venues linkVenue(UUID venueId, Organizers organizer) {
        if (venuesRepository.linkOrganizerIfFree(venueId, organizer) == 0) {
            if (!venuesRepository.existsById(venueId)) {
                throw new EntityNotFoundException("Venue not found with id " + venueId);
            }
            throw new OrganizerRequestConflictException(VENUE_ALREADY_LINKED, "Venue " + venueId + " is already linked to an organizer");
        }
        // Se carga después del UPDATE: así la entidad ya trae el organizador nuevo
        return venuesRepository.findById(venueId).orElseThrow();
    }

    /** PENDING por llegada (la más antigua primero); APPROVED y REJECTED, la última revisada primero.
     * CANCELLED no tiene reviewedAt (la cancela el usuario o deleteAsAdmin): va por updatedAt. */
    private Sort sortFor(OrganizerRequestStatus status) {
        return switch (status) {
            case PENDING -> Sort.by(Sort.Direction.ASC, "submittedAt");
            case APPROVED, REJECTED -> Sort.by(Sort.Direction.DESC, "reviewedAt");
            case CANCELLED -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
    }

    /** Organizador pedido en una CLAIM. Sin verificar cuenta como inexistente: mismo mensaje, para no revelar borradores. */
    private Organizers findClaimTarget(UUID organizerId) {
        Organizers target = organizersRepository.findById(organizerId)
                .filter(Organizers::isVerified)
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + organizerId));
        if (target.isClaimed() || target.getUser() != null) {
            throw new OrganizerAlreadyClaimedException("Organizer " + organizerId + " already has an owner");
        }
        return target;
    }

    private boolean isOnePendingViolation(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        return cause != null && cause.contains(ONE_PENDING_CONSTRAINT);
    }

    private void assertOwnership(OrganizerRequests request, UUID userId) {
        if (!request.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You are not owner of this organizer request");
        }
    }
}