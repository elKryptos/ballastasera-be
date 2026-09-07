# Roadmap de API — próximos bloques de trabajo

Checklist de lo que falta para que organizadores puedan publicar contenido real y
los usuarios tengan más interacción social. Marcar `[x]` a medida que se completa
cada endpoint. Actualizar este archivo es parte de "terminar" una tarea, no un
extra.

Convención de estado por bloque:
- `[ ]` no empezado
- `[~]` en progreso (dejar una nota de qué falta)
- `[x]` hecho y probado (Postman/tests)

---

## Bloque 1 — CRUD de contenido del organizador (bloqueante)

El CRUD de Events, Venues y Event Series ya está disponible.

### Events (`/rest/events`, auth + ownership organizer)
- [x] POST `/rest/events` — crear evento (DTO `EventCreateDto`)
- [x] PATCH `/rest/events/{id}` — editar evento propio
- [x] DELETE `/rest/events/{id}` — borrar/cancelar evento propio
- [x] PATCH `/rest/events/{id}/status` — publicar / despublicar / cancelar (`EventStatus`)
- [x] Validar ownership: `event.organizer.user.id == principal.getId()`

Pruebas automatizadas: `EventsServiceImplTest` (14), `EventsControllerTest` (8) y
`SecurityConfigTest` (11). La suite Maven completa pasa con 43 tests.

### Venues (`/rest/venues`)
- [x] GET `/rest/venues` — listado público / autocomplete (`cityId` + `search` opcional)
- [x] GET `/rest/venues/{id}` — detalle público (`VenueDetailDto`)
- [x] POST `/rest/venues` — crea venue publico reutilizable para no repetir direción
- [x] PATCH `/rest/venues/{id}` — editar venue propio (dueño = organizer que lo creó)
- [x] DELETE `/rest/admin/venues/{id}` — solo ADMIN (no el organizer creador); bloquea si el
      venue tiene eventos activos (cualquier status distinto de `CANCELLED`)

Decisión: el venue es un recurso reusable entre organizadores (varios eventos de distintos
organizers pueden apuntar al mismo venue), así que el creador **no** puede borrarlo
unilateralmente — se movió a `AdminController` bajo `/rest/admin/venues/{id}`, protegido por
`hasRole("ADMIN")` en `SecurityConfig`. El PATCH sí sigue siendo del dueño original.

Sin tests automatizados todavía — falta `VenuesServiceImplTest` y `VenuesControllerTest`
(la suite Maven sigue en 43 tests, ninguno cubre Venues). Pendiente antes de dar el bloque
por cerrado del todo.

### Event Series (`/rest/event-series`)
- [x] Schema `event_series` actualizado (venue, city, address/lat/lng, is_free/price/currency,
      flyer/instagram/whatsapp, start_time/end_time) + tabla puente `event_series_dance_styles`
- [x] Entity `EventSeries` mapeada al schema nuevo
- [x] DTOs (`EventSeriesCreateDto`, `EventSeriesUpdateDto`, `EventSeriesDetailDto`,
      `EventSeriesSummaryDto`) + `EventSeriesMapper`
- [x] `EventSeriesRepository` + `EventSeriesService`/`Impl` (create/update/delete con ownership
      y geocoding, vía `EventResolverService` compartido con Events)
- [x] POST `/rest/event-series` — crear serie recurrente (rrule)
- [x] PATCH `/rest/event-series/{id}` — editar serie propia
- [x] DELETE `/rest/event-series/{id}` — borrar serie propia
- [x] DELETE `/rest/event-series/{id}/venue` — desvincular venue de la serie
- [x] GET `/rest/event-series/{id}` — detalle público
- [x] GET `/rest/organizers/{id}/event-series` — listado público por organizador
- [ ] Definir cómo se generan las instancias de `Events` a partir de la rrule. Dos opciones
      evaluadas, sin decidir todavía:
      - **A (recomendada)**: generar N ocurrencias al crear la serie (ej. próximas 8 semanas) +
        job diario (`@Scheduled`) que agrega la siguiente ocurrencia para mantener una ventana
        rodante hacia adelante. El organizer ve eventos apenas crea la serie.
      - **B**: solo el job, nada al crear — más simple de escribir pero la serie queda vacía
        (sin ninguna ocurrencia visible) hasta que corre el job por primera vez.
      No hace falta librería de recurrencia (tipo rrule.js/ical4j): el schema solo soporta
      `FREQ=WEEKLY;BYDAY=...`, así que alcanza con parsear el `BYDAY` (2-3 códigos de día
      separados por coma) y calcular las próximas fechas que caen en esos días.
- [ ] **Deuda conocida**: `EventSeriesCreateDto`/`EventSeriesUpdateDto` no validan que `startTime`
      sea anterior a `endTime` (`Events` sí lo hace vía `@ValidEventTiming` + un chequeo extra en
      `EventsServiceImpl.update()`). Hoy se puede crear/editar una serie con `endTime` antes que
      `startTime` sin ningún error. Pendiente de fixear — mismo patrón que `Events`
      (`InvalidEventTimingException`, ya tiene handler en `BackendErrorResponse`).

Refactor de paso: `resolveCity`/`resolveVenue`/`resolveDanceStyles`/geocoding-si-falta-lat-lng
estaban duplicados entre `EventsServiceImpl` y `EventSeriesServiceImpl` — se extrajeron a
`EventResolverService`/`Impl`, usado por ambos.

Sin tests automatizados todavía.

---

## Bloque 2 — Catálogos públicos

- [x] GET `/rest/cities` — listado (para poblar filtros/mapa)
- [x] GET `/rest/cities/{id}`
- [x] GET `/rest/dance-styles` — listado
- [x] GET `/rest/dance-styles/{id}`

### City event list (`/rest/cities/{slug}/events`)
- [x] GET `/rest/cities/{slug}/events` — lista pública paginada por ciudad
- [x] Filtros opcionales `from`, `to` y `danceStyle`
- [x] Solapamiento semiabierto de fechas y estilos con lógica OR sin duplicados
- [x] Normalización de estilos CSV y validación de paginación
- [x] Tests de controller, service y repository para bordes temporales, estilos inexistentes y metadatos de página

Pruebas automatizadas relevantes: `CitiesControllerTest`, `EventsServiceImplTest` y
`EventsRepositoryTest`. La suite debe finalizar sin failures ni errores; no se fija un número
total de tests porque la matriz puede crecer.

---

## Bloque 3 — Follow de organizadores

Distinto de Favorite (que es sobre un evento puntual). Un usuario sigue a un
organizador para enterarse de sus próximos eventos.

- [ ] Entidad `Follow` (composite key userId+organizerId, como `Favorites`)
- [ ] Migración SQL tabla `follows`
- [ ] POST `/rest/organizers/{id}/follow`
- [ ] DELETE `/rest/organizers/{id}/follow`
- [ ] GET `/rest/organizers/{id}/follow` — check si el user actual sigue
- [ ] GET `/rest/users/me/following` — organizadores que sigo
- [ ] GET `/rest/organizers/{id}/followers/count` — contador público (sin exponer lista, mismo criterio que attendees)

---

## Bloque 4 — Media upload

`flyerUrl`, `logoUrl`, `avatarUrl` son hoy strings sueltos; no hay endpoint de subida.

- [ ] Elegir storage (S3 / Cloudinary / similar) — decisión de infra, no de código
- [ ] POST `/rest/media/upload` (o por recurso: `/rest/events/{id}/flyer`) — devuelve URL
- [ ] Validación de tipo/tamaño de archivo
- [ ] Borrado de media huérfana al reemplazar/eliminar el recurso

---

## Bloque 5 — Notificaciones (in-app primero, push después)

Depende de Bloque 3 (Follow) para tener sentido completo.

- [ ] Entidad `Notification` (userId, tipo, payload, leída/no leída, timestamp)
- [ ] Trigger: organizador publica evento → notificar a followers
- [ ] Trigger: evento marcado INTERESTED es "mañana" → recordatorio
- [ ] GET `/rest/users/me/notifications` (paginado)
- [ ] PATCH `/rest/users/me/notifications/{id}/read`
- [ ] Push (FCM/APNs) — solo si hay app móvil, evaluar después

---

## Bloque 6 — Nice to have (después de lo anterior)

- [ ] Búsqueda full-text: GET `/rest/events/search?q=&cityId=&danceStyleId=`
- [ ] Reviews/ratings de organizador o venue
- [ ] Comentarios en evento
- [ ] Reporte de contenido (evento falso/ofensivo)
- [ ] `@PreAuthorize`/matcher de rol ORGANIZER en `SecurityConfig` en vez de chequeo manual de ownership repetido en cada service

---

## Notas de decisiones pendientes (resolver antes de picar código)

- ¿Un organizador puede tener varios venues/series, o 1:1? (ya hay M:N en el modelo, confirmar UX)
- ¿Quién puede editar un evento de un `EventSeries`: solo la instancia o toda la serie de una vez?
Propuesta: edición individual vía `PATCH /rest/events/{id}` (ya soportado); editar la serie
   (`PATCH /rest/event-series/{id}`) solo afecta ocurrencias futuras aún no generadas, nunca
  retroactivo (mismo criterio que Google Calendar/similares — evita re-sincronizar en cascada).
- ¿Las ocurrencias de `Events` generadas desde una serie nacen en `PENDING` (requieren aprobación
  una por una, tedioso para algo semanal) o heredan el estado ya aprobado, dado que el organizer
  que las genera ya está verificado?
- Media: ¿subida directa desde backend o URL prefirmada (presigned) al storage?
