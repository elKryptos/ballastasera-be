package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.ReportStatus;
import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.exceptions.ModerationConflictException;
import com.kryptosystems.ballastasera.exceptions.SocialActionException;
import com.kryptosystems.ballastasera.models.dtos.UserReportCreateDto;
import com.kryptosystems.ballastasera.models.entities.UserReports;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.repositories.UserReportsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.ModerationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final UserReportsRepository userReportsRepository;
    private final UsersRepository usersRepository;

    @Override
    @Transactional
    public void report(UUID reporterId, UUID reportedId, UserReportCreateDto body) {
        if (reporterId.equals(reportedId)) {
            throw new SocialActionException(SocialActionException.SELF_ACTION, "You cannot report yourself");
        }
        Users reported = findActiveUser(reportedId);
        if (userReportsRepository.existsByReporterIdAndReportedIdAndStatus(reporterId, reportedId, ReportStatus.OPEN)) {
            return;
        }
        UserReports report = new UserReports();
        report.setReporter(usersRepository.getReferenceById(reporterId));
        report.setReported(reported);
        report.setReason(body.getReason());
        report.setNote(StringUtils.hasText(body.getNote()) ? body.getNote().strip() : null);
        userReportsRepository.save(report);
    }

    @Override
    public Page<UserReports> findReportsByStatus(ReportStatus status, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortFor(status));
        return userReportsRepository.findByStatusWithDetails(status, sorted);
    }

    @Override
    public UserReports findReportById(UUID reportId) {
        return userReportsRepository.findByIdWithDetails(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found with id " + reportId));
    }

    @Override
    public long countOpenReportsAgainst(UUID reportedId) {
        return userReportsRepository.countByReportedIdAndStatus(reportedId, ReportStatus.OPEN);
    }

    /** Con BANNED, ban() cierra tambien esta: findByReportedIdAndStatus devuelve la misma instancia gestionada. */
    @Override
    @Transactional
    public UserReports reviewReport(UUID reportId, UUID adminId, ReportStatus status) {
        UserReports report = findReportById(reportId);
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ModerationConflictException(ModerationConflictException.REPORT_NOT_OPEN,
                    "Report " + reportId + " was already reviewed");
        }
        if (status == ReportStatus.BANNED) {
            ban(report.getReported().getId(), adminId);
        } else {
            close(report, ReportStatus.DISMISSED, findAdmin(adminId), OffsetDateTime.now());
        }
        return report;
    }

    /** Si ya estaba baneado se mantiene banned_at; las denuncias abiertas se cierran igual. */
    @Override
    @Transactional
    public void ban(UUID userId, UUID adminId) {
        Users user = findActiveUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ModerationConflictException(ModerationConflictException.CANNOT_BAN_ADMIN,
                    "Admins cannot be banned");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!user.isBanned()) {
            user.setBanned(true);
            user.setBannedAt(now);
        }
        Users admin = findAdmin(adminId);
        userReportsRepository.findByReportedIdAndStatus(userId, ReportStatus.OPEN)
                .forEach(report -> close(report, ReportStatus.BANNED, admin, now));
    }

    @Override
    @Transactional
    public void unban(UUID userId) {
        Users user = findActiveUser(userId);
        user.setBanned(false);
        user.setBannedAt(null);
    }

    @Override
    public Page<Users> findBanned(Pageable pageable) {
        return usersRepository.findBanned(pageable);
    }

    private void close(UserReports report, ReportStatus status, Users admin, OffsetDateTime now) {
        report.setStatus(status);
        report.setReviewedBy(admin);
        report.setReviewedAt(now);
    }

    /** Cargado y no getReferenceById: el controller mapea reviewedBy.displayName fuera de la transaccion,
     * y con un proxy fallaria si open-in-view se desactiva. */
    private Users findAdmin(UUID adminId) {
        return usersRepository.findById(adminId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + adminId));
    }

    private Users findActiveUser(UUID userId) {
        return usersRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + userId));
    }

    /** OPEN: las mas viejas primero, se atienden por orden. Revisadas: las ultimas revisadas primero. */
    private Sort sortFor(ReportStatus status) {
        return switch (status) {
            case OPEN -> Sort.by(Sort.Direction.ASC, "createdAt");
            case DISMISSED, BANNED -> Sort.by(Sort.Direction.DESC, "reviewedAt");
        };
    }
}
