package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.exceptions.OrganizerAlreadyClaimedException;
import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.repositories.OrganizerRequestsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.EmailService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.utilities.InstagramUtils;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizersServiceImpl implements OrganizersService {

    private final OrganizersRepository organizersRepository;
    private final UsersService usersService;
    private final EmailService emailService;
    private final OrganizerMapper organizerMapper;
    private final OrganizerRequestsRepository organizerRequestsRepository;

    @Override
    public List<Organizers> findAll() {
        return organizersRepository.findAll();
    }

    @Override
    public Organizers findById(UUID id) {
        return organizersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with id " + id));
    }

    @Override
    public Organizers findBySlug(String slug) {
        return organizersRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with slug " + slug));
    }

    @Override
    public List<Organizers> findByUserId(UUID userId) {
        return organizersRepository.findByUserId(userId);
    }

    @Override
    public Organizers save(Organizers organizer) {
        return organizersRepository.save(organizer);
    }

    @Override
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public Organizers update(UUID id, UUID requesterId, OrganizerUpdateDto dto) {
        Organizers organizer = findById(id);
        assertOwnership(organizer, requesterId);
        organizerMapper.updateOrganizerFromDto(dto, organizer);
        organizer.setInstagram(InstagramUtils.normalizeHandle(organizer.getInstagram()));
        return organizersRepository.save(organizer);
    }

    @Override
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public Organizers updateAsAdmin(UUID id, OrganizerUpdateDto dto) {
        Organizers organizer = findById(id);
        organizerMapper.updateOrganizerFromDto(dto, organizer);
        organizer.setInstagram(InstagramUtils.normalizeHandle(organizer.getInstagram()));
        return organizersRepository.save(organizer);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public void delete(UUID id, UUID requesterId){
        Organizers organizer = findById(id);
        assertOwnership(organizer, requesterId);
        organizersRepository.delete(organizer);
        usersService.demoteToUser(requesterId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheConfig.VENUE_DETAIL, RedisCacheConfig.EVENT_DETAIL}, allEntries = true)
    public void deleteAsAdmin(UUID id) {
        Organizers organizer = findById(id);
        Users owner = organizer.getUser();

        List<OrganizerRequests> pendingClaims = organizerRequestsRepository
                .findByOrganizerIdAndTypeAndStatus(id, OrganizerRequestType.CLAIM, OrganizerRequestStatus.PENDING);
        for (OrganizerRequests claim : pendingClaims) {
            claim.setStatus(OrganizerRequestStatus.CANCELLED);
            claim.setRejectionMessage("La pagina è stata rimossa.");
        }
        organizersRepository.delete(organizer);
        if (owner != null) {
            usersService.demoteToUser(owner.getId());
        }
    }

    @Override
    public Organizers findVerifiedBySlug(String slug){
        return organizersRepository.findBySlugAndIsVerifiedTrue(slug)
                .orElseThrow(() -> new EntityNotFoundException("Organizer not found with slug " + slug));
    }

    @Override
    public void verifyExistsById(UUID id) {
        if (!organizersRepository.existsByIdAndIsVerifiedTrue(id)) {
            throw new EntityNotFoundException("Organizer not found with id " + id);
        }
    }

    @Override
    public Organizers createUnclaimed(OrganizerCreateDto dto) {
        Organizers organizer = organizerMapper.toOrganizerEntity(dto);
        organizer.setInstagram(InstagramUtils.normalizeHandle(organizer.getInstagram()));
        organizer.setSlug(SlugUtils.uniqueSlug(dto.getName(),
                slug -> organizersRepository.findBySlug(slug).isPresent()));
        organizer.setVerified(true);
        organizer.setClaimed(false);
        return organizersRepository.save(organizer);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, allEntries = true)
    public Organizers claim(UUID organizerId, UUID userId) {
        Organizers organizer = findById(organizerId);
        if (organizer.isClaimed()) {
            throw new OrganizerAlreadyClaimedException("Organizer " + organizerId + " is already claimed");
        }
        Users user = usersService.findById(userId);
        organizer.setUser(user);
        organizer.setClaimed(true);
        Organizers saved = organizersRepository.save(organizer);
        usersService.promoteToOrganizer(userId);
        emailService.sendOrganizerClaimedEmail(user.getEmail(), organizer.getName());
        return saved;
    }

    @Override
    public Page<Organizers> findPendingVerification(Pageable pageable){
        return organizersRepository.findByIsVerifiedFalse(pageable);
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, allEntries = true)
    public Organizers verify(UUID id) {
        Organizers organizer = findById(id);
        organizer.setVerified(true);
        Organizers saved = organizersRepository.save(organizer);
        if (organizer.getUser() == null) {
            return saved;
        }
        usersService.promoteToOrganizer(organizer.getUser().getId());
        emailService.sendOrganizerApprovedEmail(organizer.getUser().getEmail(), organizer.getName());
        return saved;
    }

    @Override
    public Page<Organizers> findVerified(Pageable pageable) {
        return organizersRepository.findByIsVerifiedTrue(pageable);
    }

    @Override
    public List<Organizers> findClaimable(String name, int limit) {
        // name (mín. 3) y limit (1-10) ya los valida el controller
        return organizersRepository.findByIsVerifiedTrueAndClaimedFalseAndUserIsNullAndNameContainingIgnoreCaseOrderByNameAsc(
                name.trim(), Limit.of(limit));
    }

    private void assertOwnership(Organizers organizer, UUID requesterId){
        if (organizer.getUser() == null || !organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("You are not owner of this organizer");
        }
    }
}
