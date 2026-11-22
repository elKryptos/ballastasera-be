package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.AttendeeDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
