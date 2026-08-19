# Probar la API con datos ficticios

## 1. Cargar los datos

Corre `src/main/resources/db/seed_test_data.sql` contra tu base de datos (después del schema
base con Milano + dance styles ya sembrados). Los horarios usan `now() +/- INTERVAL`, así que
siguen siendo válidos sin importar cuándo lo ejecutes — no hace falta re-generarlo cada día.

IDs fijos para pegar directo en Postman (no hace falta hacer `SELECT` antes):

| Qué | ID |
|---|---|
| Organizer "Tropical Milano" | `11111111-1111-1111-1111-111111111111` |
| Evento **EN VIVO ahora** — Salsa al Tropical | `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Evento **próximo hoy** — Bachata Sensual Night | `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |
| Evento **próximo en 3 días** — Rueda de Casino | `cccccccc-cccc-cccc-cccc-cccccccccccc` |
| Evento **pasado** (no debe aparecer nunca) | `dddddddd-dddd-dddd-dddd-dddddddddddd` |
| Evento **PENDING** (no debe aparecer nunca) | `eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee` |

---

## 2. Endpoints públicos (sin token)

### `GET /api/events`

```
GET http://localhost:8080/api/events?minLat=45.40&maxLat=45.53&minLng=9.10&maxLng=9.28
```

**Esperado**: 3 eventos en la respuesta — `aaaa` (`liveNow: true`), `bbbb` y `cccc`
(`liveNow: false`). **`dddd` y `eeee` NO deben aparecer** — si aparecen, algo está mal en el
filtro de la query (`status`/fecha).

### `GET /api/events/{id}`

```
GET http://localhost:8080/api/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa   ok
```

**Esperado**: `liveNow: true`, `goingCount: 3`, `interestedCount: 0`, `organizer.instagram:
"tropicalmilano"`. Como este evento no tiene `instagram_url` propio, `instagramUrl` en la
respuesta debe caer al del organizador (`tropicalmilano`) — es el fallback que hablamos.

```
GET http://localhost:8080/api/events/dddddddd-dddd-dddd-dddd-dddddddddddd   ok
```

**Esperado**: esto sí debe funcionar (detalle por id no filtra por fecha, solo el listado del
mapa lo hace) — sirve para confirmar que un evento pasado sigue siendo consultable si alguien
tiene el link directo, solo no aparece "flotando" en el mapa.

```
GET http://localhost:8080/api/events/00000000-0000-0000-0000-000000000000   ok
```

**Esperado**: `404` con `{"error": "Event not found with id ..."}`.

### `GET /api/events/{id}/attendees`

```
GET http://localhost:8080/api/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendees   ok
```

**Esperado**: 2 items — **Ana** (`ana.baila`) y **Marco** (`marco_salsero`). **Lucia NO debe
aparecer** aunque el `goingCount` del detalle sea 3 — ella no activó `show_profile_public`. Si
Lucia aparece acá, la privacidad está rota.

```
GET http://localhost:8080/api/events/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/attendees
```

**Esperado**: página vacía (`content: []`) — Ana marcó `INTERESTED` en este evento, no `GOING`,
así que no debe contar como asistente.

---

## 3. Endpoints con login (requieren JWT)

El login sigue siendo por Google — no hay endpoint de login "de prueba". Para conseguir un token:

1. Abre en el navegador: `http://localhost:8080/oauth2/authorization/google`
2. Completa el login con tu cuenta de Google
3. Te redirige a `app.frontend.oauth2-redirect-uri` con `?token=...` en la URL — copia ese valor
4. En Postman, pestaña **Authorization** → tipo **Bearer Token** → pega el token

Ese primer login crea tu usuario real en `users` (vía `CustomOidcUserService`) — es distinto de
los usuarios ficticios del seed, que nunca inician sesión, solo existen para poblar listas.

### `PATCH /auth/me`

```
PATCH http://localhost:8080/auth/me
Authorization: Bearer <tu token>
Content-Type: application/json

{ "instagram": "@mi.usuario", "showProfilePublic": true }
```

**Esperado**: `200` con tu perfil actualizado, `instagram: "mi.usuario"` (sin el `@`, se
normaliza en el backend).

### `POST /api/events/{id}/attendance`

```
POST http://localhost:8080/api/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendance
Authorization: Bearer <tu token>
Content-Type: application/json

{ "status": "GOING" }
```

**Esperado**: `204 No Content`. Después de esto, `GET /api/events/aaaa.../attendees` debe
mostrarte a ti también (si activaste `showProfilePublic`) y `goingCount` en el detalle debe subir
a 4.

Repite el `POST` con `{"status": "INTERESTED"}` — debe actualizar tu registro existente (upsert),
no crear uno duplicado; y ahora deberías desaparecer de `/attendees` porque ya no estás `GOING`.

### `DELETE /api/events/{id}/attendance`

```
DELETE http://localhost:8080/api/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendance
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content`, y tu registro desaparece de `event_attendance`.

### Probar que sin token, todo lo anterior falla

```
POST http://localhost:8080/api/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendance
Content-Type: application/json

{ "status": "GOING" }
```

**Esperado**: `401/403` — confirma que `SecurityConfig` sigue protegiendo lo que debe, aunque el
`GET` del mismo recurso sea público.

---

## 4. CRUD de eventos (dueño autenticado)

El organizer `11111111-1111-1111-1111-111111111111` del seed pertenece al usuario ficticio
`22222222-...`, que nunca inicia sesión. Para probar `create`/`update`/`delete` necesitás un
organizer que pertenezca a **tu** usuario real (el que logueaste por Google en la sección 3):

```sql
-- si ya tenés un organizer propio, solo asegurate que esté verificado:
UPDATE organizers SET is_verified = true WHERE id = '<tu_organizer_id>';

-- si no tenés ninguno, creá uno apuntando a tu user_id real:
INSERT INTO organizers (id, user_id, name, slug, type, is_verified)
VALUES (gen_random_uuid(), '<tu_user_id>', 'Mi Organizer de Prueba', 'mi-organizer-de-prueba', 'PERSON', true)
RETURNING id;
```

`Authorization: Bearer <tu token>` en las cuatro requests siguientes.

### `POST /api/events`

```
POST http://localhost:8080/api/events
Content-Type: application/json

{
  "organizerId": "<tu_organizer_id>",
  "cityId": 1,
  "title": "Noche de Salsa",
  "description": "Fiesta social con clase previa",
  "startAt": "2026-09-01T22:00:00+02:00",
  "endAt": "2026-09-02T02:00:00+02:00",
  "isFree": false,
  "price": 10.00,
  "currency": "EUR",
  "address": "Carrer de Balmes 100, Barcelona",
  "latitude": 41.3921,
  "longitude": 2.1595,
  "danceStyleIds": [1, 2]
}
```

**Esperado**: `201` + `EventDetailDto`. El evento se guarda internamente con estado `PENDING`, pero
el DTO de respuesta actual no expone `status`. Guardate el `id` devuelto para los siguientes pasos.
Si `organizerId` no te pertenece o no está `is_verified`, debe dar `403`.

### `PATCH /api/events/{id}`

```
PATCH http://localhost:8080/api/events/<id_del_evento_creado>
Content-Type: application/json

{
  "cityId": 1,
  "title": "Noche de Salsa (actualizado)",
  "address": "Carrer de Balmes 100, Barcelona",
  "startAt": "2026-09-01T23:00:00+02:00"
}
```

**Esperado**: `200` con el título y horario actualizados. Todos los campos de `EventUpdateDto` son
opcionales; solo se validan los campos que envíes. Los campos que no mandes quedan sin tocar gracias
al `nullValuePropertyMappingStrategy = IGNORE` del mapper.

### `PATCH /api/events/{id}/status`

```
PATCH http://localhost:8080/api/events/<id_del_evento_creado>/status
Content-Type: application/json

{ "status": "PUBLISHED" }
```

**Esperado**: `200` con `EventDetailDto`. El estado se actualiza en el servidor, pero el DTO de
respuesta actual no expone `status`. Valores válidos: `DRAFT`, `PENDING`, `PUBLISHED`, `CANCELLED`.
Con otro usuario (no dueño) debe dar `403`.

### `DELETE /api/events/{id}`

```
DELETE http://localhost:8080/api/events/<id_del_evento_creado>
```

**Esperado**: `204 No Content`. Un `GET` posterior al mismo `id` debe dar `404`.

---

## 5. CRUD de venues

El seed trae un venue reutilizable ya creado:

| Qué | ID |
|---|---|
| Venue "Sala Havana" (Milano, sin eventos activos) | `33333333-3333-3333-3333-333333333333` |

### `GET /api/venues` (público, listado/autocomplete)

```
GET http://localhost:8080/api/venues?cityId=1
```

**Esperado**: `200` con un array que incluye "Sala Havana".

```
GET http://localhost:8080/api/venues?cityId=1&search=havana
```

**Esperado**: mismo resultado filtrado — confirma que `search` no distingue mayúsculas/minúsculas.

### `GET /api/venues/{id}` (público, detalle)

```
GET http://localhost:8080/api/venues/33333333-3333-3333-3333-333333333333
```

**Esperado**: `200` con `VenueDetailDto` completo — `organizerName: "Tropical Milano"`,
`cityName: "Milano"`, `address`, `description`, `createdAt`/`updatedAt`.

```
GET http://localhost:8080/api/venues/00000000-0000-0000-0000-000000000000
```

**Esperado**: `404` con `{"message": "Venue not found with id ..."}`.

### `POST /api/venues` (dueño autenticado, organizer verificado)

Usa el mismo `<tu_organizer_id>` verificado de la sección 4.

```
POST http://localhost:8080/api/venues
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "organizerId": "<tu_organizer_id>",
  "cityId": 1,
  "name": "Mi Sala de Prueba",
  "address": "Via Tortona 20, Milano",
  "postalCode": "20144",
  "description": "Venue de prueba para Postman",
  "latitude": 45.4522,
  "longitude": 9.1620
}
```

**Esperado**: `201` + `VenuesSummaryDto`. Guardate el `id` devuelto (`<venue_id_creado>`) para
los pasos siguientes. Repetir el mismo `name` + `cityId` debe dar `409` (`DuplicateVenueException`).
Si omitís `latitude`/`longitude`, el backend geocodifica la `address` contra Nominatim — puede
tardar ~1s y depende de que la dirección sea real.

### `PATCH /api/venues/{id}` (dueño autenticado)

```
PATCH http://localhost:8080/api/venues/<venue_id_creado>
Authorization: Bearer <tu token>
Content-Type: application/json

{ "description": "Descripción actualizada" }
```

**Esperado**: `200` con `VenueDetailDto`, solo `description` cambia (resto queda intacto gracias
al `nullValuePropertyMappingStrategy = IGNORE`). Con otro usuario (no dueño del organizer) debe
dar `403`.

```
PATCH http://localhost:8080/api/venues/33333333-3333-3333-3333-333333333333
Authorization: Bearer <tu token>
Content-Type: application/json

{ "name": "Sala Havana" }
```

**Esperado**: `403` (no sos el dueño de este venue del seed) — si en cambio das `409`, revisá
el orden ownership-vs-duplicado en `VenuesServiceImpl.update`.

### `DELETE /api/admin/venues/{id}` (solo ADMIN)

El borrado **no** es del dueño — es admin-only. Para probarlo, promové tu usuario real a
`ADMIN` (no hace falta volver a loguearte, el rol se lee de la DB en cada request):

```sql
UPDATE users SET role = 'ADMIN' WHERE id = '<tu_user_id>';
```

```
DELETE http://localhost:8080/api/admin/venues/<venue_id_creado>
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content` (tu venue de prueba no tiene eventos). Un `GET` posterior al
mismo `id` debe dar `404`.

```
DELETE http://localhost:8080/api/admin/venues/33333333-3333-3333-3333-333333333333
Authorization: Bearer <tu token>
```

**Esperado**: `409` (`VenueHasActiveEventsException`) — "Sala Havana" tiene eventos `PUBLISHED`
del seed (`aaaa`, `bbbb`, `cccc`) asociados.

```sql
-- baja tu rol de nuevo para seguir probando como usuario normal
UPDATE users SET role = 'ORGANIZER' WHERE id = '<tu_user_id>';
```

```
DELETE http://localhost:8080/api/admin/venues/33333333-3333-3333-3333-333333333333
Authorization: Bearer <tu token>
```

**Esperado**: `403` — confirma que `hasRole("ADMIN")` en `SecurityConfig` bloquea a un
usuario autenticado que no sea admin, aunque sea el dueño del venue.

---

## 6. CRUD de Event Series

Mismo organizer verificado de las secciones 4 y 5 (`<tu_organizer_id>`). No hay ninguna serie
en el seed, hay que crearla primero para poder probar el resto.

### `POST /api/event-series` (dueño autenticado, organizer verificado)

```
POST http://localhost:8080/api/event-series
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "organizerId": "<tu_organizer_id>",
  "venueId": "33333333-3333-3333-3333-333333333333",
  "cityId": 1,
  "title": "Jueves de Salsa",
  "rrule": "FREQ=WEEKLY;BYDAY=TH",
  "description": "Clase + social todos los jueves",
  "isFree": false,
  "price": 8.00,
  "currency": "EUR",
  "address": "Via Tortona 20, Milano",
  "latitude": 45.4522,
  "longitude": 9.1620,
  "startTime": "21:30:00",
  "endTime": "01:00:00",
  "danceStyleIds": [1, 2]
}
```

**Esperado**: `201` + `EventSeriesDetailDto`. Guardate el `id` devuelto (`<series_id_creado>`)
para los pasos siguientes. Si `organizerId` no te pertenece o no está `is_verified`, debe dar
`403`. Si `venueId` pertenece a otra ciudad que la de `cityId`, debe dar `400`
(`VenueCityMismatchException`).

### `GET /api/event-series/{id}` (público)

```
GET http://localhost:8080/api/event-series/<series_id_creado>
```

**Esperado**: `200` con `EventSeriesDetailDto` — `venueName: "Sala Havana"`, `cityName: "Milano"`,
`danceStyles` con los dos estilos elegidos, `free: false`, `price: 8.00`.

```
GET http://localhost:8080/api/event-series/00000000-0000-0000-0000-000000000000
```

**Esperado**: `404` con `{"message": "Event series not found with id ..."}`.

### `GET /api/organizers/{id}/event-series` (público, listado)

```
GET http://localhost:8080/api/organizers/11111111-1111-1111-1111-111111111111/event-series
```

**Esperado**: `200` con un array que incluye "Jueves de Salsa" (`EventSeriesSummaryDto`).

### `PATCH /api/event-series/{id}` (dueño autenticado)

```
PATCH http://localhost:8080/api/event-series/<series_id_creado>
Authorization: Bearer <tu token>
Content-Type: application/json

{ "price": 10.00, "startTime": "22:00:00" }
```

**Esperado**: `200` con `EventSeriesDetailDto`, solo `price` y `startTime` cambian (resto queda
intacto gracias al `nullValuePropertyMappingStrategy = IGNORE`). Con otro usuario (no dueño) debe
dar `403`.

```
PATCH http://localhost:8080/api/event-series/<series_id_creado>
Authorization: Bearer <tu token>
Content-Type: application/json

{ "cityId": 1, "venueId": "33333333-3333-3333-3333-333333333333" }
```

**Esperado**: `200` sin cambios de negocio (mismo venue/ciudad) — sirve para confirmar que mandar
`cityId` sin `venueId` (o viceversa) no rompe nada, ahora que el bug de `update()` está corregido.

### `DELETE /api/event-series/{id}/venue` (dueño autenticado)

```
DELETE http://localhost:8080/api/event-series/<series_id_creado>/venue
Authorization: Bearer <tu token>
```

**Esperado**: `200` con `EventSeriesDetailDto` y `venueName: null` — la serie sigue existiendo,
solo pierde el vínculo al venue (igual que `DELETE /api/events/{id}/venue`).

### `DELETE /api/event-series/{id}` (dueño autenticado)

```
DELETE http://localhost:8080/api/event-series/<series_id_creado>
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content`. Un `GET` posterior al mismo `id` debe dar `404`.
