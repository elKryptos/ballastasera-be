package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.AttendeeDto;
import com.kryptosystems.ballastasera.models.dtos.AttendeesActivity;
import com.kryptosystems.ballastasera.models.dtos.EventAttendanceStatusDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AttendeeMapper {

    /** Una fila de «Chi ci va»: la persona y su marca en esta serata. relation null sin login. */
    @Mapping(target = "userId", source = "attendance.user.id")
    @Mapping(target = "displayName", source = "attendance.user.displayName")
    @Mapping(target = "avatarUrl", source = "attendance.user.avatarUrl")
    @Mapping(target = "instagram", source = "attendance.user.instagram")
    @Mapping(target = "lookingForPartner", source = "attendance.lookingForPartner")
    @Mapping(target = "relation", source = "relation")
    AttendeeDto toDto(EventAttendance attendance, SocialRelation relation);

    /** «Chi ci va» visto por quien mira. Sin login, relation null. */
    default Page<AttendeeDto> toDtoPage(AttendeesActivity activity) {
        return activity.attendees().map(attendance -> {
            if (activity.followedIds() == null) {
                return toDto(attendance, null);
            }
            UUID userId = attendance.getUser().getId();
            return toDto(attendance, SocialRelation.of(activity.followedIds().contains(userId),
                    activity.followerIds().contains(userId)));
        });
    }

    /** GET /events/{id}/attendance/me. attendance null = no va. */
    default EventAttendanceStatusDto toStatusDto(EventAttendance attendance) {
        EventAttendanceStatusDto dto = new EventAttendanceStatusDto();
        dto.setGoing(attendance != null);
        dto.setLookingForPartner(attendance != null && attendance.isLookingForPartner());
        return dto;
    }
}
