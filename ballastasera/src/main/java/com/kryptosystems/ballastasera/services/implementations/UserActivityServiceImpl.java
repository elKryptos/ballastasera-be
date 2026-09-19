package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.dtos.AttendanceCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Favorites;
import com.kryptosystems.ballastasera.models.mappers.EventAttendanceMapper;
import com.kryptosystems.ballastasera.models.mappers.FavoritesMapper;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.services.manager.UserActivityService;
import com.kryptosystems.ballastasera.utilities.EventTimingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private final FavoritesRepository favoritesRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final FavoritesMapper favoritesMapper;
    private final EventAttendanceMapper eventAttendanceMapper;


    @Override
    public List<EventCardDto> getMyFavorites(UUID userId) {
        List<Favorites> favorites = favoritesRepository.findByUserIdWithEventDetails(userId);
        OffsetDateTime now = OffsetDateTime.now();
        return favorites.stream()
                .map(f -> {
                    EventCardDto dto = favoritesMapper.toCardDto(f);
                    dto.setLiveNow(EventTimingUtils.isLiveNow(f.getEvent(), now));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<AttendanceCardDto> getMyAttendance(UUID userId) {
        List<EventAttendance> attendances = eventAttendanceRepository.findByUserIdWithEventDetails(userId);
        OffsetDateTime now = OffsetDateTime.now();
        return attendances.stream()
                .map(a -> {
                    AttendanceCardDto dto  = eventAttendanceMapper.toCardDto(a);
                    dto.getEvent().setLiveNow(EventTimingUtils.isLiveNow(a.getEvent(), now));
                    return dto;
                })
                .toList();
    }

}
