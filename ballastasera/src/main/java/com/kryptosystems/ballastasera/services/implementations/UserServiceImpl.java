package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import com.kryptosystems.ballastasera.models.dtos.AttendanceCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.mappers.EventAttendanceMapper;
import com.kryptosystems.ballastasera.models.mappers.FavoritesMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.services.manager.UserService;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final FavoritesRepository favoritesRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final FavoritesMapper favoritesMapper;
    private final EventAttendanceMapper eventAttendanceMapper;


    @Override
    public List<EventCardDto> getMyFavorites(UUID userId) {
        List<Favorites> favorites = favoritesRepository.findByUserIdWithEventDetails(userId);
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<UUID> eventIds = favorites.stream()
                .map(f -> f.getEvent().getId())
                .toList();
        Map<UUID, Long> goingCounts = goingCountsByEventId(eventIds);
        OffsetDateTime now = OffsetDateTime.now();
        return favorites.stream()
                .map(f -> {
                    EventCardDto dto = favoritesMapper.toCardDto(f);
                    dto.setLiveNow(EventTimingUtils.isLiveNow(f.getEvent(), now));
                    dto.setGoingCount(goingCounts.getOrDefault(f.getEvent().getId(), 0L));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<AttendanceCardDto> getMyAttendance(UUID userId) {
        List<EventAttendance> attendances = eventAttendanceRepository.findByUserIdWithEventDetails(userId);
        if (attendances.isEmpty()) {
            return List.of();
        }
        List<UUID> eventIds = attendances.stream()
                .map(a -> a.getEvent().getId())
                .toList();
        Map<UUID, Long> goingCounts = goingCountsByEventId(eventIds);
        OffsetDateTime now = OffsetDateTime.now();
        return attendances.stream()
                .map(a -> {
                    AttendanceCardDto dto  = eventAttendanceMapper.toCardDto(a);
                    dto.getEvent().setLiveNow(EventTimingUtils.isLiveNow(a.getEvent(), now));
                    dto.getEvent().setGoingCount(goingCounts.getOrDefault(a.getEvent().getId(), 0L));
                    return dto;
                })
                .toList();
    }

    private Map<UUID, Long> goingCountsByEventId(List<UUID> eventIds) {
        return eventAttendanceRepository.countByEventIdInAndStatus(eventIds, AttendanceStatus.GOING).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

}
