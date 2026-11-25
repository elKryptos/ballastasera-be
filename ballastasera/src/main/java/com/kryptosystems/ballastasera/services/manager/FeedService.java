package com.kryptosystems.ballastasera.services.manager;

import com.kryptosystems.ballastasera.models.dtos.FeedActivity;
import com.kryptosystems.ballastasera.models.dtos.SuggestionsActivity;

import java.util.UUID;

/**
 * Pestaña Amici: feed (Attività) y sugerencias (Scopri). Todo se calcula en el momento a partir de los follows y los
 * «Ci vado»: no hay historial ni cache, porque depende de quien mira.
 */
public interface FeedService {

    /**
     * «Stasera escono»: serate en curso o que empiezan antes de las proximas 06:00 hora de Italia.
     * «Novità» de los ultimos 7 dias: quien empezo a seguirlo y «Ci vado» a serate de los proximos 7 dias.
     * Las serate («Stasera escono» y «va a X»): solo de gente que sigue sin silenciar, con perfil publico y
     * respetando a quien deja ver sus serate. «ti segue»: cualquiera que lo siga, tambien privado
     * (profilePublic = false), salvo quien silencio.
     */
    FeedActivity findFeed(UUID viewerId);

    /** limit: 1–20. Gente de sus proximas serate y amigos de amigos; nunca a quien ya sigue, bloqueados ni menores. */
    SuggestionsActivity findSuggestions(UUID viewerId, int limit);
}
