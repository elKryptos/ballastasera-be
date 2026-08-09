package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.dtos.OrganizerCreateDto;
import com.kryptosystems.ballastasera.models.dtos.OrganizerUpdateDto;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
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
    public void deleteById(UUID id) {
        organizersRepository.deleteById(id);
    }

    @Override
    public Organizers createForUser(UUID userId, OrganizerCreateDto dto) {
        Users user = usersService.findById(userId);

        Organizers organizer = new Organizers();
        organizer.setUser(user);
        organizer.setName(dto.getName());
        organizer.setSlug(uniqueSlug(dto.getName()));
        organizer.setType(dto.getType());
        organizer.setDescription(dto.getDescription());
        organizer.setLogoUrl(dto.getLogoUrl());
        organizer.setWebsite(dto.getWebsite());
        organizer.setPhone(dto.getPhone());
        organizer.setContactEmail(dto.getContactEmail());
        organizer.setInstagram(dto.getInstagram());
        organizer.setFacebook(dto.getFacebook());
        organizer.setVerified(false);
        return organizersRepository.save(organizer);
    }

    private String uniqueSlug(String name) {
        String base = SlugUtils.slugify(name);
        String slug = base;
        int attempt = 1;
        while (organizersRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + attempt++;
        }
        return slug;
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
        usersService.promoteToOrganizer(organizer.getUser().getId());
        emailService.sendOrganizerApprovedEmail(organizer.getUser().getEmail(), organizer.getName());
        return saved;
    }

    @Override
    public Page<Organizers> findVerified(Pageable pageable) {
        return organizersRepository.findByIsVerifiedTrue(pageable);
    }

    @Override
    public Organizers update(UUID id, UUID requesterId, OrganizerUpdateDto dto) {
        Organizers organizer = findById(id);
        if (!organizer.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this organizer");
        }
        organizer.setName(dto.getName());
        organizer.setDescription(dto.getDescription());
        organizer.setLogoUrl(dto.getLogoUrl());
        organizer.setWebsite(dto.getWebsite());
        organizer.setPhone(dto.getPhone());
        organizer.setContactEmail(dto.getContactEmail());
        organizer.setInstagram(dto.getInstagram());
        organizer.setFacebook(dto.getFacebook());
        return organizersRepository.save(organizer);
    }
}
