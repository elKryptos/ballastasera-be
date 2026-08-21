package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.OrganizerAlreadyClaimedException;
import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.EmailService;
import com.kryptosystems.ballastasera.services.manager.OrganizersService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.utilities.SlugUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizersServiceImpl implements OrganizersService {

    private final OrganizersRepository organizersRepository;
    private final UsersService usersService;
    private final EmailService emailService;
    private final OrganizerMapper organizerMapper;

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
    public Organizers createForUser(UUID userId, OrganizerCreateDto dto) {
        Users user = usersService.findById(userId);
        Organizers organizer = organizerMapper.toOrganizerEntity(dto);
        organizer.setUser(user);
        organizer.setSlug(SlugUtils.uniqueSlug(dto.getName(),
                slug -> organizersRepository.findBySlug(slug).isPresent()));
        organizer.setVerified(false);
        return organizersRepository.save(organizer);
    }

    @Override
    public Organizers update(UUID id, UUID requesterId, OrganizerUpdateDto dto) {
        Organizers organizer = findById(id);
        assertOwnership(organizer, requesterId);
        organizerMapper.updateOrganizerFromDto(dto, organizer);
        return organizersRepository.save(organizer);
    }

    @Override
    public void delete(UUID id, UUID requesterId){
        Organizers organizer = this.findById(id);
        assertOwnership(organizer, requesterId);
        organizersRepository.delete(organizer);
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
        organizer.setSlug(SlugUtils.uniqueSlug(dto.getName(),
                slug -> organizersRepository.findBySlug(slug).isPresent()));
        organizer.setVerified(true);
        organizer.setClaimed(false);
        return organizersRepository.save(organizer);
    }

    @Override
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

    private void assertOwnership(Organizers organizer, UUID requesterId){
        if (organizer.getUser() == null || !organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("You are not owner of this organizer");
        }
    }
}
