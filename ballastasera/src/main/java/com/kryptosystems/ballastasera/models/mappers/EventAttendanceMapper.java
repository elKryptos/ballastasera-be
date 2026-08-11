package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.AttendanceCardDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Delega en EventsMapper para no duplicar el mapeo de campos de Events. */
@Component
@RequiredArgsConstructor
public class EventAttendanceMapper {

    private final EventsMapper eventsMapper;

    public AttendanceCardDto toCardDto(EventAttendance attendance) {
        AttendanceCardDto dto = new AttendanceCardDto();
        dto.setEvent(eventsMapper.toEventCardDto(attendance.getEvent()));
        dto.setStatus(attendance.getStatus());
        return dto;
    }

    public List<AttendanceCardDto> toCardDtos(List<EventAttendance> attendances) {
        return attendances.stream().map(this::toCardDto).toList();
    }
}