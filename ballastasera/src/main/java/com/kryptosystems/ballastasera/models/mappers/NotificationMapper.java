package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.CursorPageDto;
import com.kryptosystems.ballastasera.models.dtos.NotificationDto;
import com.kryptosystems.ballastasera.models.dtos.NotificationsActivity;
import com.kryptosystems.ballastasera.models.dtos.NotificationsUnreadDto;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.Notifications;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Campanita: NotificationsActivity → CursorPageDto<NotificationDto>. actor y event llegan ya cargados por los
 * JOIN FETCH de NotificationsRepository.findVisible.
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /** Igual que UserMapper.toSummaryDto: los default de aqui no pueden llamar a un metodo de otro mapper. */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "profilePublic", source = "user.showProfilePublic")
    UserSummaryDto toSummaryDto(Users user, SocialRelation relation);

    /** El cursor es la fecha y el id del ultimo aviso. */
    default CursorPageDto<NotificationDto> toCursorPage(NotificationsActivity activity) {
        CursorPageDto<NotificationDto> dto = new CursorPageDto<>();
        dto.setItems(activity.notifications().getContent().stream()
                .map(notification -> toDto(notification, activity))
                .toList());
        dto.setTotal(activity.total());
        if (activity.notifications().hasNext() && !activity.notifications().isEmpty()) {
            Notifications last = activity.notifications().getContent().getLast();
            dto.setNextCursorStartAt(last.getCreatedAt());
            dto.setNextCursorId(last.getId());
        }
        return dto;
    }

    /** El numero sobre la campanita. */
    default NotificationsUnreadDto toUnreadDto(long unread) {
        return new NotificationsUnreadDto(unread);
    }

    /** relation de hoy, no la del momento del aviso: «Ricambia» desaparece si ya lo sigue. */
    private NotificationDto toDto(Notifications notification, NotificationsActivity activity) {
        Users actor = notification.getActor();
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setRead(notification.getReadAt() != null);
        dto.setActor(toSummaryDto(actor, SocialRelation.of(activity.followedIds().contains(actor.getId()),
                activity.followerIds().contains(actor.getId()))));
        Events event = notification.getEvent();
        if (event != null) {
            dto.setEventId(event.getId());
            dto.setEventSlug(event.getSlug());
            dto.setEventTitle(event.getTitle());
            dto.setEventStartAt(event.getStartAt());
            dto.setPhrase(notification.getInvitePhrase());
        }
        return dto;
    }
}
