package com.kryptosystems.ballastasera.models.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** GET /users/me/notifications/unread-count: el numero sobre la campanita. */
@Getter
@Setter
@AllArgsConstructor
public class NotificationsUnreadDto {
    private long unread;
}
