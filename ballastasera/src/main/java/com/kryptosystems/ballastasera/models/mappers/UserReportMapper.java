package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.BannedUserAdminDto;
import com.kryptosystems.ballastasera.models.dtos.UserReportAdminDto;
import com.kryptosystems.ballastasera.models.entities.UserReports;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Denuncias y baneados para el admin. reporter, reported y reviewedBy llegan ya cargados por los JOIN FETCH
 * de UserReportsRepository: sin ellos habria N+1 en la cola.
 */
@Mapper(componentModel = "spring")
public interface UserReportMapper {

    /** Sin el contador: lo pone toUserReportAdminDto(report, openReportsAgainst). */
    @Mapping(target = "reporterId", source = "reporter.id")
    @Mapping(target = "reporterName", source = "reporter.displayName")
    @Mapping(target = "reporterAvatarUrl", source = "reporter.avatarUrl")
    @Mapping(target = "reportedId", source = "reported.id")
    @Mapping(target = "reportedName", source = "reported.displayName")
    @Mapping(target = "reportedAvatarUrl", source = "reported.avatarUrl")
    @Mapping(target = "reportedEmail", source = "reported.email")
    @Mapping(target = "reportedBanned", source = "reported.banned")
    @Mapping(target = "reviewedByName", source = "reviewedBy.displayName")
    @Mapping(target = "openReportsAgainst", ignore = true)
    UserReportAdminDto toUserReportAdminDtoBase(UserReports report);

    default UserReportAdminDto toUserReportAdminDto(UserReports report, long openReportsAgainst) {
        UserReportAdminDto dto = toUserReportAdminDtoBase(report);
        dto.setOpenReportsAgainst(openReportsAgainst);
        return dto;
    }

    @Mapping(target = "userId", source = "id")
    BannedUserAdminDto toBannedUserAdminDto(Users user);
}
