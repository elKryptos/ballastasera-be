package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.models.entities.Notifications;
import org.springframework.data.domain.Slice;

import java.util.Set;
import java.util.UUID;

/**
 * Una pagina de la campanita. No se devuelve tal cual: NotificationMapper la copia en CursorPageDto<NotificationDto>,
 * como FeedActivity en FeedDto.
 * <p>
 * notifications: los avisos de la pagina, con actor y serata ya cargados (JOIN FETCH).
 * followedIds: de los actores, a quienes sigue quien mira. followerIds: quienes lo siguen a el. Con los dos sale la
 * relation de cada aviso (el boton «Ricambia» de «ti segue»).
 * total: todos los avisos visibles, con el mismo filtro.
 */
public record NotificationsActivity(Slice<Notifications> notifications,
                                    Set<UUID> followedIds,
                                    Set<UUID> followerIds,
                                    long total) {
}
