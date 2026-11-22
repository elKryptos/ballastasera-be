package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.AttendeeDto;
import com.kryptosystems.ballastasera.models.dtos.EventAttendanceStatusDto;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.keys.UserEventId;
import com.kryptosystems.ballastasera.models.mappers.AttendeeMapper;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.EventAttendanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventAttendanceServiceImpl implements EventAttendanceService {

    private static final int AVATAR_PREVIEW_LIMIT = 3;

    /** «Chi ci va» sin login: un id que no es de nadie, asi la query de bloqueos no excluye a nadie.
     * Evita pasar null a la query (Postgres no sabe el tipo de un parametro null). */
    static final UUID ANONYMOUS_VIEWER = new UUID(0L, 0L);

    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsRepository eventsRepository;
    private final UsersRepository usersRepository;
    private final UserFollowsRepository userFollowsRepository;
    private final AttendeeMapper attendeeMapper;
    private final UserMapper userMapper;

    @Override
    public List<EventAttendance> findByUserId(UUID userId) {
        return eventAttendanceRepository.findByUserId(userId);
    }

    @Override
    public List<EventAttendance> findByEventId(UUID eventId) {
        return eventAttendanceRepository.findByEventId(eventId);
    }

    @Override
    public EventAttendance save(EventAttendance attendance) {
        return eventAttendanceRepository.save(attendance);
    }

    @Override
    public void deleteById(UserEventId id) {
        eventAttendanceRepository.deleteById(id);
    }

    @Override
    public Page<AttendeeDto> findPublicAttendees(UUID eventId, UUID viewerId, Pageable pageable) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }

        UUID viewer = viewerId != null ? viewerId : ANONYMOUS_VIEWER;
        Page<EventAttendance> page = eventAttendanceRepository.findPublicByEventId(eventId, viewer,
                ContactVisibility.EVERYONE, ContactVisibility.MUTUALS, pageable);
        if (viewerId == null) {
            return page.map(ea -> attendeeMapper.toDto(ea, null));
        }

        // Relacion de cada fila con dos queries para toda la pagina (sin N+1)
        List<UUID> ids = page.getContent().stream().map(ea -> ea.getUser().getId()).toList();
        Set<UUID> viewerFollows = ids.isEmpty() ? Set.of() : userFollowsRepository.findFollowedIdsAmong(viewerId, ids);
        Set<UUID> followViewer = ids.isEmpty() ? Set.of() : userFollowsRepository.findFollowerIdsAmong(viewerId, ids);
        return page.map(ea -> {
            UUID attendeeId = ea.getUser().getId();
            SocialRelation relation = SocialRelation.of(viewerFollows.contains(attendeeId), followViewer.contains(attendeeId));
            return attendeeMapper.toDto(ea, relation);
        });
    }

    /** Sin save() cuando ya iba: la fila esta "managed" en la transaccion y el cambio se guarda al commit. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#eventId")
    public void addAttendance(UUID userId, UUID eventId, Boolean lookingForPartner) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        Optional<EventAttendance> existing = eventAttendanceRepository.findById(id);
        if (existing.isPresent()) {
            if (lookingForPartner != null) {
                existing.get().setLookingForPartner(lookingForPartner);
            }
            return;
        }
        EventAttendance attendance = new EventAttendance();
        attendance.setUser(usersRepository.getReferenceById(userId));
        attendance.setEvent(eventsRepository.getReferenceById(eventId));
        attendance.setLookingForPartner(Boolean.TRUE.equals(lookingForPartner));
        eventAttendanceRepository.save(attendance);
        eventsRepository.incrementGoingCount(eventId);
    }

    @Override
    public EventAttendanceStatusDto findMyAttendanceStatus(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        Optional<EventAttendance> attendance = eventAttendanceRepository.findById(id);
        EventAttendanceStatusDto dto = new EventAttendanceStatusDto();
        dto.setGoing(attendance.isPresent());
        dto.setLookingForPartner(attendance.map(EventAttendance::isLookingForPartner).orElse(false));
        return dto;
    }

    @Override
    public List<UserSummaryDto> findFriendsGoing(UUID viewerId, UUID eventId) {
        if (!eventsRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id " + eventId);
        }
        return eventAttendanceRepository.findFriendsGoing(eventId, viewerId, ContactVisibility.NOBODY).stream()
                .map(friend -> userMapper.toSummaryDto(friend, SocialRelation.MUTUAL))
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, key = "#eventId")
    public void removeAttendance(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        if (eventAttendanceRepository.existsById(id)) {
            eventAttendanceRepository.deleteById(id);
            eventsRepository.decrementGoingCount(eventId);
        }
    }

    @Override
    public boolean isGoing(UUID userId, UUID eventId) {
        UserEventId id = new UserEventId();
        id.setUserId(userId);
        id.setEventId(eventId);
        return eventAttendanceRepository.existsById(id);
    }

    @Override
    public List<EventAttendance> findAvatarPreviews(List<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return eventAttendanceRepository.findAvatarPreviewsByEventIdIn(eventIds, AVATAR_PREVIEW_LIMIT,
                ContactVisibility.EVERYONE);
    }
}