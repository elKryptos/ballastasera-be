package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.InvitePhrase;
import com.kryptosystems.ballastasera.enums.NotificationType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un aviso de la campanita. actor lleva la relacion con quien mira (boton «Ricambia») y profilePublic: si es false,
 * el FE lo muestra sin enlace y con «Profilo privato». Los campos de la serata y phrase, solo en INVITE.
 */
@Getter
@Setter
public class NotificationDto {
    private UUID id;
    private NotificationType type;
    private OffsetDateTime createdAt;
    private boolean read;
    private UserSummaryDto actor;
    private UUID eventId;
    private String eventSlug;
    private String eventTitle;
    private OffsetDateTime eventStartAt;
    private InvitePhrase phrase;
}
