package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.models.entities.UserFollows;
import org.springframework.data.domain.Slice;

import java.util.Set;
import java.util.UUID;

/**
 * Una pagina de /me/followers, /me/following o /me/friends. No se devuelve tal cual: UserMapper la copia en
 * CursorPageDto<UserSummaryDto>, como FeedActivity en FeedDto.
 * <p>
 * follows: los follows de la pagina, con la otra persona ya cargada (JOIN FETCH).
 * friendIds: de esas personas, quienes son amigos de quien mira (relation MUTUAL).
 * total: el total de la lista, con el mismo filtro.
 */
public record FollowsActivity(Slice<UserFollows> follows, Set<UUID> friendIds, long total) {
}
