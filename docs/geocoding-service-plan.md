# GeocodingService — piano di implementazione

## Problema di partenza (Punto 4)

`Events.latitude` / `Events.longitude` sono `NOT NULL` a DB, ma `EventCreateDto`
non li obbliga via validazione — deciso di non aggiungere `@NotNull` perché
chiedere all'utente di inserire manualmente le coordinate e' scomodo. Soluzione
scelta: calcolare `latitude`/`longitude` lato backend a partire dall'indirizzo
(`address`, eventualmente `cityId`) al momento della creazione/modifica evento,
invece di richiederli come input obbligatorio.

## Decisione presa

- **Provider di geocoding: Nominatim (OpenStreetMap)**, gratuito, nessuna API
  key, nessuna carta di credito richiesta. Limite reale: 1 richiesta/secondo
  (accettabile per il volume di creazioni/modifiche eventi previsto).
- **Frontend**: Leaflet + tile OpenStreetMap (gratuito). Nota: la scelta di
  Leaflet in FE e' indipendente dal provider di geocoding in BE — non c'e'
  vincolo di coerenza tra i due (a differenza di Google Maps + Google
  Geocoding, che converrebbe usare insieme).
- Pensato dietro un'interfaccia `GeocodingService` cosi' che in futuro si possa
  sostituire l'implementazione (es. Google Geocoding API) senza toccare
  `EventsServiceImpl`.

## Punti ancora da decidere in implementazione

1. **Dove agganciarlo**: `EventsServiceImpl.create()` (e `update()` quando
   cambia `address`). Se il DTO porta gia' `latitude`/`longitude` espliciti
   (es. utente ha trascinato un pin sulla mappa per affinare la posizione),
   quelli vanno rispettati e il geocoding va saltato — il geocoding e' solo un
   fallback/default calcolato dall'indirizzo, non un override forzato.
2. **Gestione fallimenti**: Nominatim puo' non trovare risultati per un
   indirizzo ambiguo/mal scritto, o essere irraggiungibile. In quel caso la
   creazione dell'evento non puo' comunque andare a buon fine (colonna DB
   NOT NULL) — va restituito un 400 chiaro tipo "indirizzo non trovato,
   correggi o seleziona la posizione manualmente sulla mappa", non un 500.
3. **Rate limit 1 req/sec**: da rispettare lato client HTTP (es. throttling
   locale) se in futuro si aggiungono geocodifiche bulk (es. import massivo di
   eventi/venue). Per singole creazioni via API non e' un problema pratico.
4. **User-Agent obbligatorio**: il ToS di Nominatim richiede un header
   `User-Agent` identificativo dell'applicazione nelle richieste (es.
   `Ballastasera/1.0 (contatto@dominio)`), altrimenti le richieste possono
   essere bloccate.
5. **Venues**: anche `Venues.latitude`/`longitude` sono `NOT NULL` a DB e
   hanno lo stesso problema di `Events`. Da valutare se riusare lo stesso
   `GeocodingService` quando si implementera' la creazione di Venue via API
   (nota: oggi manca ancora un `VenuesController` con endpoint di creazione,
   vedi analisi generale del progetto).

## Prossimo passo

Scrivere:
- `GeocodingService` (interfaccia) + `NominatimGeocodingService` (implementazione,
  chiamata HTTP a `https://nominatim.openstreetmap.org/search`).
- Hook in `EventsServiceImpl.create()` / `update()` per usarlo come fallback
  quando `latitude`/`longitude` non arrivano dal client.
- Gestione esplicita del caso "non trovato" con un errore 400 leggibile
  (probabilmente una nuova eccezione dedicata gestita in `BackendErrorResponse`).
