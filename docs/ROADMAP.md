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

El CRUD de Events ya está disponible; Venues también. Event Series tiene la capa de
servicio lista, falta el controller.

### Events (`/api/events`, auth + ownership organizer)
- [x] POST `/api/events` — crear evento (DTO `EventCreateDto`)
- [x] PATCH `/api/events/{id}` — editar evento propio
- [x] DELETE `/api/events/{id}` — borrar/cancelar evento propio
- [x] PATCH `/api/events/{id}/status` — publicar / despublicar / cancelar (`EventStatus`)
- [x] Validar ownership: `event.organizer.user.id == principal.getId()`

Pruebas automatizadas: `EventsServiceImplTest` (14), `EventsControllerTest` (8) y
`SecurityConfigTest` (11). La suite Maven completa pasa con 43 tests.

### Venues (`/api/venues`)
- [x] GET `/api/venues` — listado público / autocomplete (`cityId` + `search` opcional)
- [x] GET `/api/venues/{id}` — detalle público (`VenueDetailDto`)
- [x] POST `/api/venues` — crea venue publico reutilizable para no repetir direción
- [x] PATCH `/api/venues/{id}` — editar venue propio (dueño = organizer que lo creó)
- [x] DELETE `/api/admin/venues/{id}` — solo ADMIN (no el organizer creador); bloquea si el
      venue tiene eventos activos (cualquier status distinto de `CANCELLED`)

Decisión: el venue es un recurso reusable entre organizadores (varios eventos de distintos
organizers pueden apuntar al mismo venue), así que el creador **no** puede borrarlo
unilateralmente — se movió a `AdminController` bajo `/api/admin/venues/{id}`, protegido por
`hasRole("ADMIN")` en `SecurityConfig`. El PATCH sí sigue siendo del dueño original.

Sin tests automatizados todavía — falta `VenuesServiceImplTest` y `VenuesControllerTest`
(la suite Maven sigue en 43 tests, ninguno cubre Venues). Pendiente antes de dar el bloque
por cerrado del todo.

### Event Series (`/api/event-series`)
- [x] Schema `event_series` actualizado (venue, city, address/lat/lng, is_free/price/currency,
      flyer/instagram/whatsapp, start_time/end_time) + tabla puente `event_series_dance_styles`
- [x] Entity `EventSeries` mapeada al schema nuevo
- [x] DTOs (`EventSeriesCreateDto`, `EventSeriesUpdateDto`, `EventSeriesDetailDto`,
      `EventSeriesSummaryDto`) + `EventSeriesMapper`
- [x] `EventSeriesRepository` + `EventSeriesService`/`Impl` (create/update/delete con ownership
      y geocoding, vía `EventResolverService` compartido con Events)
- [~] POST `/api/event-series` — crear serie recurrente (rrule) — falta el controller
- [~] PATCH `/api/event-series/{id}` — editar — falta el controller
- [~] DELETE `/api/event-series/{id}` — falta el controller
- [ ] Definir cómo se generan las instancias de `Events` a partir de la rrule (job o al crear)

Refactor de paso: `resolveCity`/`resolveVenue`/`resolveDanceStyles`/geocoding-si-falta-lat-lng
estaban duplicados entre `EventsServiceImpl` y `EventSeriesServiceImpl` — se extrajeron a
`EventResolverService`/`Impl`, usado por ambos.

Sin tests automatizados todavía.

---

## Bloque 2 — Catálogos públicos

- [x] GET `/api/cities` — listado (para poblar filtros/mapa)
- [x] GET `/api/cities/{id}`
- [x] GET `/api/dance-styles` — listado
- [x] GET `/api/dance-styles/{id}`

Pruebas automatizadas: `CitiesControllerTest`, `CitiesServiceImplTest`, `DanceStylesControllerTest`,
`DanceStylesServiceImplTest`, `SecurityConfigTest` y `CitiesRepositoryTest`. La suite Maven completa pasa con 43 tests.

---

## Bloque 3 — Follow de organizadores

Distinto de Favorite (que es sobre un evento puntual). Un usuario sigue a un
organizador para enterarse de sus próximos eventos.

- [ ] Entidad `Follow` (composite key userId+organizerId, como `Favorites`)
- [ ] Migración SQL tabla `follows`
- [ ] POST `/api/organizers/{id}/follow`
- [ ] DELETE `/api/organizers/{id}/follow`
- [ ] GET `/api/organizers/{id}/follow` — check si el user actual sigue
- [ ] GET `/api/users/me/following` — organizadores que sigo
- [ ] GET `/api/organizers/{id}/followers/count` — contador público (sin exponer lista, mismo criterio que attendees)

---

## Bloque 4 — Media upload

`flyerUrl`, `logoUrl`, `avatarUrl` son hoy strings sueltos; no hay endpoint de subida.

- [ ] Elegir storage (S3 / Cloudinary / similar) — decisión de infra, no de código
- [ ] POST `/api/media/upload` (o por recurso: `/api/events/{id}/flyer`) — devuelve URL
- [ ] Validación de tipo/tamaño de archivo
- [ ] Borrado de media huérfana al reemplazar/eliminar el recurso

---

## Bloque 5 — Notificaciones (in-app primero, push después)

Depende de Bloque 3 (Follow) para tener sentido completo.

- [ ] Entidad `Notification` (userId, tipo, payload, leída/no leída, timestamp)
- [ ] Trigger: organizador publica evento → notificar a followers
- [ ] Trigger: evento marcado INTERESTED es "mañana" → recordatorio
- [ ] GET `/api/users/me/notifications` (paginado)
- [ ] PATCH `/api/users/me/notifications/{id}/read`
- [ ] Push (FCM/APNs) — solo si hay app móvil, evaluar después

---

## Bloque 6 — Nice to have (después de lo anterior)

- [ ] Búsqueda full-text: GET `/api/events/search?q=&cityId=&danceStyleId=`
- [ ] Reviews/ratings de organizador o venue
- [ ] Comentarios en evento
- [ ] Reporte de contenido (evento falso/ofensivo)
- [ ] `@PreAuthorize`/matcher de rol ORGANIZER en `SecurityConfig` en vez de chequeo manual de ownership repetido en cada service

---

## Notas de decisiones pendientes (resolver antes de picar código)

- ¿Un organizador puede tener varios venues/series, o 1:1? (ya hay M:N en el modelo, confirmar UX)
- ¿Quién puede editar un evento de un `EventSeries`: solo la instancia o toda la serie de una vez?
- Media: ¿subida directa desde backend o URL prefirmada (presigned) al storage?
