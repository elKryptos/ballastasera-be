package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** GET /users/me/feed: la pestaña Attività. */
@Getter
@Setter
public class FeedDto {
    /** «Stasera escono», por hora de inicio. */
    private List<FeedTonightDto> tonight;
    /** «Novità» de los ultimos 7 dias, las mas recientes primero (hasta 50 de cada tipo). */
    private List<FeedNewsDto> news;
}
