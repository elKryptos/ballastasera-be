package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.models.dtos.EventCardDto;
import com.kryptosystems.ballastasera.models.entities.Favorites;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Delega en EventsMapper para no duplicar el mapeo de campos de Events.
 * liveNow/goingCount quedan en su valor por defecto (false/0): igual que
 * en EventsMapper, dependen de queries agregadas que resuelve el service.
 */
@Component
@RequiredArgsConstructor
public class FavoritesMapper {

    private final EventsMapper eventsMapper;

    public EventCardDto toCardDto(Favorites favorite) {
        return eventsMapper.toCardDto(favorite.getEvent());
    }

    public List<EventCardDto> toCardDtos(List<Favorites> favorites) {
        return favorites.stream().map(this::toCardDto).toList();
    }
}
