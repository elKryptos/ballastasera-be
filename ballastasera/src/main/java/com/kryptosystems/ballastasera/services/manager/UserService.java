package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.AttendanceCardDto;
import com.kryptosystems.ballastasera.models.dtos.EventCardDto;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<EventCardDto> getMyFavorites(UUID userId);
    List<AttendanceCardDto> getMyAttendance(UUID userId);
}
