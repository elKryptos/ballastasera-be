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

### `GET /rest/events`

```
GET http://localhost:8081/rest/events?minLat=45.40&maxLat=45.53&minLng=9.10&maxLng=9.28
```

**Esperado**: 3 eventos en la respuesta — `aaaa` (`liveNow: true`), `bbbb` y `cccc`
(`liveNow: false`). **`dddd` y `eeee` NO deben aparecer** — si aparecen, algo está mal en el
filtro de la query (`status`/fecha).

### `GET /rest/events/{id}`

```
GET http://localhost:8081/rest/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa   ok
```

**Esperado**: `liveNow: true`, `goingCount: 3`, `interestedCount: 0`, `organizer.instagram:
"tropicalmilano"`. Como este evento no tiene `instagram_url` propio, `instagramUrl` en la
respuesta debe caer al del organizador (`tropicalmilano`) — es el fallback que hablamos.

```
GET http://localhost:8081/rest/events/dddddddd-dddd-dddd-dddd-dddddddddddd   ok
```

**Esperado**: esto sí debe funcionar (detalle por id no filtra por fecha, solo el listado del
mapa lo hace) — sirve para confirmar que un evento pasado sigue siendo consultable si alguien
tiene el link directo, solo no aparece "flotando" en el mapa.

```
GET http://localhost:8081/rest/events/00000000-0000-0000-0000-000000000000   ok
```

**Esperado**: `404` con `{"error": "Event not found with id ..."}`.

### `GET /rest/events/{id}/attendees`

```
GET http://localhost:8081/rest/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendees   ok
```

**Esperado**: 2 items — **Ana** (`ana.baila`) y **Marco** (`marco_salsero`). **Lucia NO debe
aparecer** aunque el `goingCount` del detalle sea 3 — ella no activó `show_profile_public`. Si
Lucia aparece acá, la privacidad está rota.

```
GET http://localhost:8081/rest/events/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/attendees
```

**Esperado**: página vacía (`content: []`) — Ana marcó `INTERESTED` en este evento, no `GOING`,
así que no debe contar como asistente.

---

## 3. Endpoints con login (requieren JWT)

El login sigue siendo por Google — no hay endpoint de login "de prueba". Para conseguir un token:

1. Abre en el navegador: `http://localhost:8081/oauth2/authorization/google`
2. Completa el login con tu cuenta de Google
3. Te redirige a `app.frontend.oauth2-redirect-uri` con `?token=...` en la URL — copia ese valor
4. En Postman, pestaña **Authorization** → tipo **Bearer Token** → pega el token

Ese primer login crea tu usuario real en `users` (vía `CustomOidcUserService`) — es distinto de
los usuarios ficticios del seed, que nunca inician sesión, solo existen para poblar listas.

### `PATCH /rest/auth/me`

```
PATCH http://localhost:8081/rest/auth/me
Authorization: Bearer <tu token>
Content-Type: application/json

{ "instagram": "@mi.usuario", "showProfilePublic": true }
```

**Esperado**: `200` con tu perfil actualizado, `instagram: "mi.usuario"` (sin el `@`, se
normaliza en el backend).

### `POST /rest/events/{id}/attendance`

```
POST http://localhost:8081/rest/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendance
Authorization: Bearer <tu token>
Content-Type: application/json

{ "status": "GOING" }
```

**Esperado**: `204 No Content`. Después de esto, `GET /rest/events/aaaa.../attendees` debe
mostrarte a ti también (si activaste `showProfilePublic`) y `goingCount` en el detalle debe subir
a 4.

Repite el `POST` con `{"status": "INTERESTED"}` — debe actualizar tu registro existente (upsert),
no crear uno duplicado; y ahora deberías desaparecer de `/attendees` porque ya no estás `GOING`.

### `DELETE /rest/events/{id}/attendance`

```
DELETE http://localhost:8081/rest/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendance
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content`, y tu registro desaparece de `event_attendance`.

### Probar que sin token, todo lo anterior falla

```
POST http://localhost:8081/rest/events/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/attendance
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

### `POST /rest/events`

```
POST http://localhost:8081/rest/events
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

### `PATCH /rest/events/{id}`

```
PATCH http://localhost:8081/rest/events/<id_del_evento_creado>
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

### `PATCH /rest/events/{id}/status`

```
PATCH http://localhost:8081/rest/events/<id_del_evento_creado>/status
Content-Type: application/json

{ "status": "PUBLISHED" }
```

**Esperado**: `200` con `EventDetailDto`. El estado se actualiza en el servidor, pero el DTO de
respuesta actual no expone `status`. Valores válidos: `DRAFT`, `PENDING`, `PUBLISHED`, `CANCELLED`.
Con otro usuario (no dueño) debe dar `403`.

### `PATCH /rest/events/{id}/flyer` (dueño autenticado)

El archivo se envía como multipart en el campo `file`. La conversión a WebP se ejecuta de forma
asincrónica.

```
PATCH http://localhost:8081/rest/events/<id_del_evento_creado>/flyer
Authorization: Bearer <tu token>
Content-Type: multipart/form-data

file: flyer.jpg
```

**Esperado**: `200` con `flyerStatus: "PROCESSING"`. El backend guarda el archivo original,
convierte el flyer a WebP y publica el resultado. Repetí el detalle hasta que el estado sea
`READY`:

```
GET http://localhost:8081/rest/events/<id_del_evento_creado>
```

Cuando esté listo, `flyerUrl` debe apuntar a `http://localhost/flyers/<id_del_evento_creado>`.
Esa URL se sirve a través de nginx, que reenvía `/flyers/` al bucket `ballastasera-flyers` de RustFS.

### `PATCH /rest/admin/events/{id}/flyer` (solo ADMIN)

El admin puede subir o reemplazar el flyer de cualquier evento, sin chequeo de ownership.

```
PATCH http://localhost:8081/rest/admin/events/<id_del_evento>/flyer
Authorization: Bearer <token_de_admin>
Content-Type: multipart/form-data

file: flyer.jpg
```

**Esperado**: `200` con `flyerStatus: "PROCESSING"`. Consultá el detalle hasta obtener
`flyerStatus: "READY"`; entonces `flyerUrl` debe ser
`http://localhost/flyers/<id_del_evento>`.

### `DELETE /rest/events/{id}`

```
DELETE http://localhost:8081/rest/events/<id_del_evento_creado>
```

**Esperado**: `204 No Content`. Un `GET` posterior al mismo `id` debe dar `404`.

---

## 5. CRUD de venues

El seed trae un venue reutilizable ya creado:

| Qué | ID |
|---|---|
| Venue "Sala Havana" (Milano, sin eventos activos) | `33333333-3333-3333-3333-333333333333` |

### `GET /rest/venues` (público, listado/autocomplete)

```
GET http://localhost:8081/rest/venues?cityId=1
```

**Esperado**: `200` con un array que incluye "Sala Havana".

```
GET http://localhost:8081/rest/venues?cityId=1&search=havana
```

**Esperado**: mismo resultado filtrado — confirma que `search` no distingue mayúsculas/minúsculas.

### `GET /rest/venues/{id}` (público, detalle)

```
GET http://localhost:8081/rest/venues/33333333-3333-3333-3333-333333333333
```

**Esperado**: `200` con `VenueDetailDto` completo — `organizerName: "Tropical Milano"`,
`cityName: "Milano"`, `address`, `description`, `createdAt`/`updatedAt`.

```
GET http://localhost:8081/rest/venues/00000000-0000-0000-0000-000000000000
```

**Esperado**: `404` con `{"message": "Venue not found with id ..."}`.

### `POST /rest/venues` (dueño autenticado, organizer verificado)

Usa el mismo `<tu_organizer_id>` verificado de la sección 4.

```
POST http://localhost:8081/rest/venues
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

### `PATCH /rest/venues/{id}` (dueño autenticado)

```
PATCH http://localhost:8081/rest/venues/<venue_id_creado>
Authorization: Bearer <tu token>
Content-Type: application/json

{ "description": "Descripción actualizada" }
```

**Esperado**: `200` con `VenueDetailDto`, solo `description` cambia (resto queda intacto gracias
al `nullValuePropertyMappingStrategy = IGNORE`). Con otro usuario (no dueño del organizer) debe
dar `403`.

```
PATCH http://localhost:8081/rest/venues/33333333-3333-3333-3333-333333333333
Authorization: Bearer <tu token>
Content-Type: application/json

{ "name": "Sala Havana" }
```

**Esperado**: `403` (no sos el dueño de este venue del seed) — si en cambio das `409`, revisá
el orden ownership-vs-duplicado en `VenuesServiceImpl.update`.

### `DELETE /rest/admin/venues/{id}` (solo ADMIN)

El borrado **no** es del dueño — es admin-only. Para probarlo, promové tu usuario real a
`ADMIN` (no hace falta volver a loguearte, el rol se lee de la DB en cada request):

```sql
UPDATE users SET role = 'ADMIN' WHERE id = '<tu_user_id>';
```

```
DELETE http://localhost:8081/rest/admin/venues/<venue_id_creado>
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content` (tu venue de prueba no tiene eventos). Un `GET` posterior al
mismo `id` debe dar `404`.

```
DELETE http://localhost:8081/rest/admin/venues/33333333-3333-3333-3333-333333333333
Authorization: Bearer <tu token>
```

**Esperado**: `409` (`VenueHasActiveEventsException`) — "Sala Havana" tiene eventos `PUBLISHED`
del seed (`aaaa`, `bbbb`, `cccc`) asociados.

```sql
-- baja tu rol de nuevo para seguir probando como usuario normal
UPDATE users SET role = 'ORGANIZER' WHERE id = '<tu_user_id>';
```

```
DELETE http://localhost:8081/rest/admin/venues/33333333-3333-3333-3333-333333333333
Authorization: Bearer <tu token>
```

**Esperado**: `403` — confirma que `hasRole("ADMIN")` en `SecurityConfig` bloquea a un
usuario autenticado que no sea admin, aunque sea el dueño del venue.

### `PATCH /rest/admin/venues/{id}/logo` (solo ADMIN)

Volvé a promover tu usuario a `ADMIN` con el mismo `UPDATE users` del paso anterior.

El archivo se envía como multipart en el campo **`logo`** (no `file` como en los flyers). Acepta
JPG, PNG o WebP; el backend lo convierte a WebP (máximo 512px en el lado largo) de forma
sincrónica, así que la respuesta ya trae la URL final.

```
PATCH http://localhost:8081/rest/admin/venues/33333333-3333-3333-3333-333333333333/logo
Authorization: Bearer <token_de_admin>
Content-Type: multipart/form-data

logo: logo.png
```

**Esperado**: `200` con `VenueDetailDto` y
`logoUrl: "http://localhost/logos/venues/33333333-3333-3333-3333-333333333333/logo-<uuid>.webp"`.
Abrí esa URL en el navegador: nginx la sirve desde el bucket `ballastasera-logos`. Si subís otro
logo, la URL cambia (uuid nuevo) y el anterior se borra del bucket; así el navegador nunca
muestra un logo viejo cacheado.

- Campo con otro nombre (por ejemplo `file`) → `400` con `"message": "Missing request part: logo"`.
- Archivo que no es imagen (por ejemplo un `.gif` o un `.pdf`) → `415`.
- `id` inexistente → `404` con `"message": "Venue not found with id ..."`.

### `DELETE /rest/admin/venues/{id}/logo` (solo ADMIN)

```
DELETE http://localhost:8081/rest/admin/venues/33333333-3333-3333-3333-333333333333/logo
Authorization: Bearer <token_de_admin>
```

**Esperado**: `204 No Content`. Un `GET /rest/venues/{id}` posterior devuelve `logoUrl: null` y
el archivo ya no existe en el bucket.

---

## 6. CRUD de Event Series

Mismo organizer verificado de las secciones 4 y 5 (`<tu_organizer_id>`). No hay ninguna serie
en el seed, hay que crearla primero para poder probar el resto.

### `POST /rest/event-series` (dueño autenticado, organizer verificado)

```
POST http://localhost:8081/rest/event-series
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

### `GET /rest/event-series/{id}` (público)

```
GET http://localhost:8081/rest/event-series/<series_id_creado>
```

**Esperado**: `200` con `EventSeriesDetailDto` — `venueName: "Sala Havana"`, `cityName: "Milano"`,
`danceStyles` con los dos estilos elegidos, `free: false`, `price: 8.00`.

```
GET http://localhost:8081/rest/event-series/00000000-0000-0000-0000-000000000000
```

**Esperado**: `404` con `{"message": "Event series not found with id ..."}`.

### `GET /rest/organizers/{id}/event-series` (público, listado)

```
GET http://localhost:8081/rest/organizers/11111111-1111-1111-1111-111111111111/event-series
```

**Esperado**: `200` con un array que incluye "Jueves de Salsa" (`EventSeriesSummaryDto`).

### `PATCH /rest/event-series/{id}` (dueño autenticado)

```
PATCH http://localhost:8081/rest/event-series/<series_id_creado>
Authorization: Bearer <tu token>
Content-Type: application/json

{ "price": 10.00, "startTime": "22:00:00" }
```

**Esperado**: `200` con `EventSeriesDetailDto`, solo `price` y `startTime` cambian (resto queda
intacto gracias al `nullValuePropertyMappingStrategy = IGNORE`). Con otro usuario (no dueño) debe
dar `403`.

```
PATCH http://localhost:8081/rest/event-series/<series_id_creado>
Authorization: Bearer <tu token>
Content-Type: application/json

{ "cityId": 1, "venueId": "33333333-3333-3333-3333-333333333333" }
```

**Esperado**: `200` sin cambios de negocio (mismo venue/ciudad) — sirve para confirmar que mandar
`cityId` sin `venueId` (o viceversa) no rompe nada, ahora que el bug de `update()` está corregido.

### `DELETE /rest/event-series/{id}/venue` (dueño autenticado)

```
DELETE http://localhost:8081/rest/event-series/<series_id_creado>/venue
Authorization: Bearer <tu token>
```

**Esperado**: `200` con `EventSeriesDetailDto` y `venueName: null` — la serie sigue existiendo,
solo pierde el vínculo al venue (igual que `DELETE /rest/events/{id}/venue`).

### `DELETE /rest/event-series/{id}` (dueño autenticado)

```
DELETE http://localhost:8081/rest/event-series/<series_id_creado>
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content`. Un `GET` posterior al mismo `id` debe dar `404`.

## 7. Solicitudes «Diventa organizzatore» (usuario autenticado)

Plan: `organizer-requests-plan.md` (fase 2). Hay que hacer login con un **USER sin organizador**: si
ya tiene uno, todos los `POST` dan `409` `ORGANIZER_LIMIT_REACHED` (D3, máximo 1). Solo puede haber
una solicitud `PENDING` por usuario: cancela la anterior antes de crear otra. `venueCityId: 1` es
Milano (compruébalo con `GET /rest/cities`). Los `409` traen el motivo en `errors.code`.

### `POST /rest/organizer-requests` — NEW de tipo PERSON

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "NEW",
  "name": "DJ Kryptos",
  "organizerType": "PERSON",
  "description": "DJ di salsa e bachata a Milano.",
  "instagram": "@dj.kryptos",
  "contactEmail": "dj.kryptos@example.com",
  "phone": "+39 333 1234567",
  "website": "https://djkryptos.it",
  "message": "Suono ogni venerdì in zona Navigli.",
  "termsAccepted": true
}
```

**Esperado**: `201` + `OrganizerRequestDto` con `status: "PENDING"` e `instagram: "dj.kryptos"`
(sin `@`, lo normaliza `InstagramUtils`). Guardate el `id` (`<request_id>`).

Repetir el mismo `POST` → **Esperado**: `409` con `errors.code = "PENDING_REQUEST_EXISTS"`.

### `GET /rest/organizer-requests/me`

```
GET http://localhost:8081/rest/organizer-requests/me
Authorization: Bearer <tu token>
```

**Esperado**: `200` con la solicitud más reciente (la `PENDING` del paso anterior). Con un usuario
que nunca ha pedido nada: `204 No Content`.

### `PATCH /rest/organizer-requests/{id}/acknowledge`

```
PATCH http://localhost:8081/rest/organizer-requests/<request_id>/acknowledge
Authorization: Bearer <tu token>
```

**Esperado** con la solicitud todavía `PENDING`: `409` con `errors.code = "REQUEST_STILL_PENDING"`.
Una vez aprobada o rechazada (fase 3): `200` con `acknowledgedAt` relleno; si se llama otra vez, la
fecha no cambia (idempotente). Con el token de otro usuario: `403`.

### `DELETE /rest/organizer-requests/{id}`

```
DELETE http://localhost:8081/rest/organizer-requests/<request_id>
Authorization: Bearer <tu token>
```

**Esperado**: `204 No Content`. Después `GET /me` devuelve `status: "CANCELLED"` con `acknowledgedAt`
relleno (la canceló el propio usuario, no hay aviso que enseñarle). Un segundo `DELETE` da `409`
`REQUEST_NOT_PENDING`. Con el token de otro usuario: `403`. Con un id inventado: `404`.

### Reglas de `@ValidOrganizerRequest` (D11: dirección del local)

NEW de tipo CLUB sin dirección:

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "NEW",
  "name": "Club Caliente",
  "organizerType": "CLUB",
  "instagram": "clubcaliente.mi",
  "contactEmail": "info@clubcaliente.it",
  "termsAccepted": true
}
```

**Esperado**: `400` con `errors.venueAddress = "venueAddress is required for this organizer type"`.

NEW de tipo CLUB con dirección:

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "NEW",
  "name": "Club Caliente",
  "organizerType": "CLUB",
  "instagram": "clubcaliente.mi",
  "contactEmail": "info@clubcaliente.it",
  "venueAddress": "Via Tortona 27",
  "venueCityId": 1,
  "termsAccepted": true
}
```

**Esperado**: `201` con `venueAddress` y `venueCityName: "Milano"`. Con un `venueCityId` que no
existe: `404`. Cancelarla después (`DELETE`) para seguir probando.

NEW de tipo PERSON con dirección:

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "NEW",
  "name": "DJ Kryptos",
  "organizerType": "PERSON",
  "instagram": "dj.kryptos",
  "contactEmail": "dj.kryptos@example.com",
  "venueAddress": "Via Tortona 27",
  "venueCityId": 1,
  "termsAccepted": true
}
```

**Esperado**: `400` con `errors.venueAddress = "venueAddress is not allowed for this organizer type"`.
Si llega solo `venueAddress` o solo `venueCityId`: `400` con `"venueAddress and venueCityId go together"`.

Varios errores en una sola petición:

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "NEW",
  "instagram": "nome con spazi",
  "contactEmail": "non-email",
  "termsAccepted": false
}
```

**Esperado**: `400` con `errors` que incluye `name`, `organizerType`, `instagram`, `contactEmail` y
`termsAccepted`.

### `POST /rest/organizer-requests` — CLAIM de una página que ya existe

Usa *Salsa Loca Milano* del seed (verificada y sin dueño).

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "CLAIM",
  "targetOrganizerId": "88888888-8888-8888-8888-888888888888",
  "instagram": "salsaloca.milano",
  "contactEmail": "titolare@salsaloca.it",
  "message": "Sono la titolare, scrivetemi pure.",
  "termsAccepted": true
}
```

**Esperado**: `201` con `type: "CLAIM"` y `organizerName: "Salsa Loca Milano"` (sale de la página, el
body no lleva `name`). Sin `targetOrganizerId`: `400`. Con un UUID inventado o una página sin
verificar: `404` con el mismo mensaje en los dos casos. Si la página ya tiene dueño: `409`
`ORGANIZER_ALREADY_CLAIMED`. Cancelarla al terminar.

### `GET /rest/organizers/claimable` (público, buscador del wizard para CLAIM)

Devuelve solo las páginas verificadas, sin reclamar y **sin dueño** (`user_id` NULL), por orden
alfabético. `name` mínimo 3 caracteres; `limit` entre 1 y 10, por defecto 5.

```
GET http://localhost:8081/rest/organizers/claimable?name=sal
```

**Esperado**: `200` con `[Salsa Loca Milano]` (`OrganizerSummaryDto`).

```
GET http://localhost:8081/rest/organizers/claimable?name=tro
```

**Esperado**: `200` con `[]`. *Tropical Milano* está verificado pero tiene dueño (`claimed` sigue en
`false` porque lo creó su dueño y lo verificó el admin): no tiene que salir.

```
GET http://localhost:8081/rest/organizers/claimable?name=sa
```

**Esperado**: `400` con `errors.name` (menos de 3 caracteres).

```
GET http://localhost:8081/rest/organizers/claimable?name=sal&limit=20
```

**Esperado**: `400` con `errors.limit` (máximo 10).

```
GET http://localhost:8081/rest/organizers/claimable
```

**Esperado**: `400` (falta el parámetro `name`).

### `POST /rest/organizers` ya no existe (D10)

El perfil de organizador se pide ahora con `POST /rest/organizer-requests`.

```
POST http://localhost:8081/rest/organizers
Authorization: Bearer <tu token>
Content-Type: application/json

{ "name": "Prueba" }
```

**Esperado**: `405` con `message: "Method POST not supported"` y `details: "Supported methods: GET"`
(la ruta existe solo para el listado público). Antes del handler de
`HttpRequestMethodNotSupportedException` en `BackendErrorResponse` daba `500`.

## 8. Solicitudes «Diventa organizzatore» (admin)

Plan: `organizer-requests-plan.md` (fase 3). Todo con el **token de un ADMIN**: no hace falta el token
de los usuarios que piden. Los datos están en `src/main/resources/db/seed_test_data.sql` (plan §3.2d):

| Solicitud | Usuario | Caso |
|---|---|---|
| `ab000000-0000-0000-0000-000000000001` | Ana | CLAIM PENDING de *Salsa Loca Milano* (`88888888-…`, SCHOOL, con su venue `99999999-…`) |
| `ab000000-0000-0000-0000-000000000002` | Marco | CLAIM PENDING del mismo organizador: la que se rechaza sola al aprobar la de Ana |
| `ab000000-0000-0000-0000-000000000003` | Lucia | NEW PERSON REJECTED |
| `ab000000-0000-0000-0000-000000000004` | Lucia | NEW PERSON PENDING: «2º intento» (`previousRejections: 1`) |
| `ab000000-0000-0000-0000-000000000005` | Dario | NEW CLUB PENDING con dirección (Via Gola 12, Milano) |

Antes de empezar, comprueba que la base está como la deja el seed (las pruebas de la sección 7 y
el claim directo tocan *Salsa Loca*). Si algo no cuadra, recarga el seed:

```sql
-- 01, 02, 04 y 05 PENDING; 03 REJECTED
SELECT r.id, u.display_name, r.type, r.status
FROM organizer_requests r JOIN users u ON u.id = r.user_id
WHERE r.id::text LIKE 'ab000000%' ORDER BY r.id;

-- Salsa Loca sin dueño: user_id NULL, claimed false
SELECT id, user_id, claimed FROM organizers WHERE id = '88888888-8888-8888-8888-888888888888';

-- Vacía: ninguno de los cuatro tiene organizador (si no, 409 ORGANIZER_LIMIT_REACHED)
SELECT user_id, count(*) FROM organizers
WHERE user_id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555',
                  '66666666-6666-6666-6666-666666666666', '77777777-7777-7777-7777-777777777777')
GROUP BY user_id;
```

Los usuarios del seed tienen correos `@ballastasera.test`, que no existen. Para recibir los emails,
cámbialos por alias de tu Gmail (`tu_correo+ana@gmail.com`, `+marco`, `+lucia`, `+dario`) con un
`UPDATE users SET email = …`; si no, se ven en el panel de Resend.

### `GET /rest/admin/organizer-requests` — cola por estado

`status` es obligatorio (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`); `page` desde 0; `size`
entre 1 y 50, por defecto 20. El orden lo decide el servicio según el estado.

```
GET http://localhost:8081/rest/admin/organizer-requests?status=PENDING
Authorization: Bearer <token admin>
```

**Esperado**: `200` con `Page<OrganizerRequestAdminDto>`, la más antigua primero (`submittedAt` ASC).
`totalElements` es el contador «In attesa · N». Cada fila trae `requester` (quien la pidió) y
`previousRejections`. En una CLAIM: `targetOrganizer` con el organizador pedido y `targetOrganizerVenues`
con sus venues; en una NEW: `targetOrganizer` vacío y `targetOrganizerVenues: []`.

```
GET http://localhost:8081/rest/admin/organizer-requests?status=REJECTED
Authorization: Bearer <token admin>
```

**Esperado**: `200`, la última revisada primero (`reviewedAt` DESC), igual que `APPROVED`.
`previousRejections` no cuenta la propia fila: un usuario con un solo rechazo sale con `0`.
`CANCELLED` va por `updatedAt` DESC (no tiene `reviewedAt`).

```
GET http://localhost:8081/rest/admin/organizer-requests?status=FOO
GET http://localhost:8081/rest/admin/organizer-requests
GET http://localhost:8081/rest/admin/organizer-requests?status=PENDING&size=51
Authorization: Bearer <token admin>
```

**Esperado**: `400` en los tres (estado que no existe, falta `status`, `size` mayor que 50).

### `GET /rest/admin/organizer-requests/{id}` — detalle

```
GET http://localhost:8081/rest/admin/organizer-requests/<request_id>
Authorization: Bearer <token admin>
```

**Esperado**: `200` con el mismo `OrganizerRequestAdminDto` que en la lista. Con un id inventado: `404`.

### `PATCH /rest/admin/organizer-requests/{id}/approve`

Body opcional: `{ "venueId": "<uuid>" }`, solo para una NEW de tipo local (D11). Si algo falla (`409`,
`404`), la transacción hace rollback: no se crea el organizador, el rol no cambia y no sale ningún
email. Los emails salen solo después del commit.

**NEW de tipo PERSON (Lucia)**

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000004/approve
Authorization: Bearer <token admin>
```

**Esperado**: `200` con `status: "APPROVED"`, `organizerId`, `organizerSlug: "lucia-bachata"`,
`reviewedByName` y `reviewedAt`. Lucia queda con `role = 'ORGANIZER'` en `users` y le llega el email
«Il tuo profilo organizzatore è stato approvato».

Repetir el mismo `PATCH` → **Esperado**: `409` con `errors.code = "REQUEST_NOT_PENDING"`.

**NEW de tipo CLUB con dirección (Dario)**

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000005/approve
Authorization: Bearer <token admin>
```

**Esperado** sin body: `409` con `errors.code = "VENUE_REQUIRED"` (un CLUB necesita venue). No existe el
organizador «Tropical Club Navigli», Dario sigue siendo `USER` y no sale ningún email.

Con el venue de *Salsa Loca*, que ya tiene organizador:

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000005/approve
Authorization: Bearer <token admin>
Content-Type: application/json

{ "venueId": "99999999-9999-9999-9999-999999999999" }
```

**Esperado**: `409` con `errors.code = "VENUE_ALREADY_LINKED"`. Con un `venueId` inventado: `404`.

Crear el venue con la dirección de la solicitud (sin `organizerId`) y aprobar con su id:

```
POST http://localhost:8081/rest/admin/venues
Authorization: Bearer <token admin>
Content-Type: application/json

{ "cityId": 1, "name": "Tropical Club Navigli", "type": "CLUB", "address": "Via Gola 12" }
```

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000005/approve
Authorization: Bearer <token admin>
Content-Type: application/json

{ "venueId": "<id del venue creado>" }
```

**Esperado**: `200` con `venueId` y `venueName`. `GET /rest/venues/<id del venue>` muestra el organizador.

**CLAIM con otra CLAIM que compite (Ana gana, Marco pierde)**

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000001/approve
Authorization: Bearer <token admin>
```

**Esperado**: `200`. *Salsa Loca* queda con Ana como dueña (`claimed = true`), Ana pasa a `ORGANIZER` y
le llega el email «Il profilo organizzatore ti è stato assegnato». No hace falta `venueId`: el organizador
ya tiene su venue.

Después, `GET /rest/admin/organizer-requests?status=REJECTED`: la de Marco sale con
`rejectionReason: "DUPLICATE"`, `rejectionMessage: "La pagina è stata assegnata a un altro utente."`,
`reviewedByName` y el mismo `reviewedAt` que la aprobación. A Marco le llega el email de rechazo
(«Motivo: Doppione.» + el mensaje). Se rechazan **todas** las CLAIM PENDING de ese organizador: si quedó
alguna de la sección 7, también sale aquí (probado el 27/10 con dos a la vez).

### `PATCH /rest/admin/organizer-requests/{id}/reject`

Body obligatorio: `{ "reason": "<OrganizerRejectReason>", "message": "<opcional, máx. 500>" }`. Con
`OTHER` el mensaje es obligatorio. Al mensaje se le quitan los espacios de los extremos y, si queda
vacío, se guarda `null`. El email de rechazo sale después del commit. Probado el 29/10 con el seed recién
cargado.

**NEW con mensaje con espacios (Lucia, 2º intento)**

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000004/reject
Authorization: Bearer <token admin>
Content-Type: application/json

{ "reason": "INSTAGRAM_NOT_FOUND", "message": "   Il profilo @luciabachata è privato.   " }
```

**Esperado**: `200` con `status: "REJECTED"`, `rejectionReason: "INSTAGRAM_NOT_FOUND"`,
`rejectionMessage` sin los espacios, `reviewedByName`, `reviewedAt` y `previousRejections: 1`. Lucia sigue
`USER` y le llega el email («Motivo: Instagram non trovato.» + el mensaje).

Repetir el mismo `PATCH` → **Esperado**: `409` con `errors.code = "REQUEST_NOT_PENDING"`.

**`OTHER` sin mensaje (Dario)**

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000005/reject
Authorization: Bearer <token admin>
Content-Type: application/json

{ "reason": "OTHER" }
```

**Esperado**: `400` con `errors.messageProvidedForOther`. Igual con `"message": "   "`. Dario sigue PENDING.

**Otro motivo sin mensaje (Dario)**: `{ "reason": "INCOMPLETE_DATA", "message": "" }` → `200` con
`rejectionMessage: null`. El email sale sin mensaje.

**CLAIM: Marco rechazado, Ana sigue PENDING**

```
PATCH http://localhost:8081/rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000002/reject
Authorization: Bearer <token admin>
Content-Type: application/json

{ "reason": "NOT_DANCE_ORGANIZER", "message": "Non risulti tra i gestori della scuola." }
```

**Esperado**: `200`. El email de Marco nombra a «Salsa Loca Milano». La CLAIM de Ana (`-01`) sigue
PENDING y *Salsa Loca* sigue sin dueño (`user_id NULL`, `claimed = false`). Si después se aprueba la de
Ana, la de Marco **no cambia**: sigue `NOT_DANCE_ORGANIZER` con su mensaje y no le llega otro email
(approve solo rechaza las CLAIM que siguen PENDING).

**Validación**: sin `reason` → `400` con `errors.reason`; `message` de más de 500 caracteres → `400` con
`errors.message`; id inventado → `404`.

**Body ilegible** (handler de `HttpMessageNotReadableException`, vale para todos los `@RequestBody`;
antes daba `500`). Con el mismo `PATCH …/reject` de una solicitud PENDING:

| Body | Esperado |
|---|---|
| `{ "reason": "FOO" }` | `400` con `errors.reason = "must be one of [INSTAGRAM_NOT_FOUND, INCOMPLETE_DATA, NOT_DANCE_ORGANIZER, DUPLICATE, OTHER]"` |
| sin body | `400` sin `errors` |
| `{ "reason": ` (JSON roto) | `400` sin `errors` |
| `{ "reason": "OTHER", "message": {} }` | `400` con `errors.message = "has an invalid value"` |

`approve` no cambia: su body es opcional (`required = false`) y sin body responde como siempre.

### `409 CONCURRENT_UPDATE` — dos admins revisan la misma solicitud a la vez

El `@Version` de `organizer_requests` se comprueba en el commit. Con dos peticiones de Postman no se
consigue la carrera (la segunda llega después del commit y da `REQUEST_NOT_PENDING`), así que se simula
con el debugger:

1. Backend en **Debug** desde IntelliJ, con un breakpoint en `OrganizerRequestsServiceImpl.reject`, en la
   línea `request.setStatus(...)`, suspend **Thread**.
2. `PATCH …/ab000000-0000-0000-0000-000000000005/reject` con `{ "reason": "INCOMPLETE_DATA" }`: se queda parado.
3. Con la petición parada, en la base:
   `UPDATE organizer_requests SET version = version + 1 WHERE id = 'ab000000-0000-0000-0000-000000000005';`
4. Reanudar (F9).

**Esperado**: `409` con `errors.code = "CONCURRENT_UPDATE"` y
`message: "The resource was modified by another request"`. Rollback: la solicitud sigue PENDING y no sale
ningún email. Probado el 29/10.

*Si recargas el seed creando la base desde cero: `ballastasera_schema.sql` todavía no tiene
`organizer_requests`. Antes del seed hay que ejecutar el bloque del plan §3.2. Tu usuario admin se crea
otra vez al hacer login (`UPDATE users SET role = 'ADMIN' …`). Después vuelve a hacer login para tener un
token con el id nuevo.*

### Sin ser ADMIN

Las mismas peticiones con el token de un USER → **Esperado**: `403`. Sin token: `401`.

*Hasta el commit `5462714` un USER recibía `401`: el `403` de `hasRole("ADMIN")` se reenvía a
`/error`, ese reenvío no lleva el JWT y Spring lo trataba como anónimo. Ahora `SecurityConfig` deja
pasar el dispatch `ERROR` y el `403` llega tal cual. Vale para todo `/rest/admin/**`.*

---

## 9. Borrar organizadores y enlazar venues (admin, fase 0 de «Diventa organizzatore»)

Plan: `organizer-requests-plan.md` (fase 0). Empieza con la base como la deja el seed (ver §8) y apunta
tu id de admin: `SELECT id, role FROM users WHERE email = '<tu email>';`. Probado el 29/10.

### `DELETE /rest/admin/organizers/{id}` — organizador sin dueño con CLAIM PENDING

```
DELETE http://localhost:8081/rest/admin/organizers/88888888-8888-8888-8888-888888888888
Authorization: Bearer <token admin>
```

**Esperado**: `204`. Las solicitudes `ab000000-…-01` y `-02` pasan a `CANCELLED` con
`rejection_message = 'La pagina è stata rimossa.'`, el venue `99999999-…` queda con
`organizer_id = NULL` (`ON DELETE SET NULL`) y Ana y Marco siguen `USER`.

### `PATCH /rest/admin/venues/{id}` con `organizerId` (D11)

Seguido de lo anterior, con el venue de *Salsa Loca* ya sin organizador:

```
PATCH http://localhost:8081/rest/admin/venues/99999999-9999-9999-9999-999999999999
Authorization: Bearer <token admin>
Content-Type: application/json

{ "organizerId": "11111111-1111-1111-1111-111111111111" }
```

**Esperado**: `200` con `organizerId` y `organizerName: "Tropical Milano"`; el resto del venue no cambia.
`GET /rest/organizers/11111111-1111-1111-1111-111111111111/venues` (público) muestra *Sala Havana* y
*Salsa Loca · Sala Isola*. Con un `organizerId` inventado: `404`. Con `{}`: `200` y el enlace no cambia.

**Recarga el seed** antes de seguir.

### `demoteToUser` — el rol después de borrar un organizador

**A: ORGANIZER con un solo organizador → USER.** Aprueba la CLAIM de Ana
(`PATCH /rest/admin/organizer-requests/ab000000-0000-0000-0000-000000000001/approve`, ver §8) y borra
*Salsa Loca*:

```
DELETE http://localhost:8081/rest/admin/organizers/88888888-8888-8888-8888-888888888888
Authorization: Bearer <token admin>
```

**Esperado**: `204` y Ana vuelve a `role = 'USER'`.

**B: ORGANIZER con dos organizadores → sigue ORGANIZER.** El usuario `22222222-…` ya tiene
*Tropical Milano*; se le asigna otro y se borra:

```
POST http://localhost:8081/rest/admin/organizers/unclaimed
Authorization: Bearer <token admin>
Content-Type: application/json

{ "name": "Prova Demote B", "type": "PERSON" }
```

```
PATCH http://localhost:8081/rest/admin/organizers/<id creado>/claim
Authorization: Bearer <token admin>
Content-Type: application/json

{ "userId": "22222222-2222-2222-2222-222222222222" }
```

```
DELETE http://localhost:8081/rest/admin/organizers/<id creado>
Authorization: Bearer <token admin>
```

**Esperado**: `204` y `22222222-…` sigue `ORGANIZER`.

**C: ADMIN → nunca baja.** Igual que B, pero con `"name": "Prova Demote C"` y el claim con
`{ "userId": "<tu id de admin>" }` (te llega el email del claim). Después, con el endpoint del admin:

```
DELETE http://localhost:8081/rest/admin/organizers/<id creado>
Authorization: Bearer <token admin>
```

**Esperado**: `204` y sigues `ADMIN`.

*El 29/10 este caso se probó con el `DELETE /rest/organizers/{id}` del dueño. Desde D13 (§10), ese
endpoint da `403` para un organizador asignado por el admin, así que ahora se borra con el del admin.*

*Sin probar: `DELETE /rest/organizers/{id}` del dueño siendo USER → vuelve a USER. Hace falta el token de
una segunda cuenta de Google que no sea admin; la lógica es la misma del caso A.*

---

## 10. D13: el dueño solo borra lo que creó con una NEW (organizador)

Plan: `organizer-requests-plan.md` (fase 3, D13). `DELETE /rest/organizers/{id}` solo funciona si existe
una solicitud **NEW APPROVED** de ese usuario para ese organizador. Si le llegó por CLAIM o se lo asignó el
admin con `PATCH /rest/admin/organizers/{id}/claim` → `403` y no se borra nada. El admin sigue borrando
cualquiera con `DELETE /rest/admin/organizers/{id}`.

**✅ Probado el 29/10** (casos a–d, código en el commit de D13; 166 tests en verde).

Con tu cuenta, que es la única que tiene token: `<tu token>` y `<token admin>` son el mismo. Antes de
cada caso, tu usuario no puede tener ningún organizador ni ninguna solicitud PENDING (si no, `409`
`ORGANIZER_LIMIT_REACHED` o `PENDING_REQUEST_EXISTS`):

```sql
SELECT id, name FROM organizers WHERE user_id = '<tu id>';
SELECT id, type, status FROM organizer_requests WHERE user_id = '<tu id>' AND status = 'PENDING';
```

### a) NEW aprobada → el dueño la puede borrar

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "NEW",
  "name": "Prova D13 New",
  "organizerType": "PERSON",
  "description": "Prova D13: organizzatore creato con una NEW.",
  "instagram": "prova.d13.new",
  "contactEmail": "prova.d13@example.com",
  "termsAccepted": true
}
```

**Esperado**: `201`. Copia el `id` (`<request_id>`).

```
PATCH http://localhost:8081/rest/admin/organizer-requests/<request_id>/approve
Authorization: Bearer <token admin>
```

**Esperado**: `200` con `organizerId` (`<organizer_id>`).

```
DELETE http://localhost:8081/rest/organizers/<organizer_id>
Authorization: Bearer <tu token>
```

**Esperado**: `204`. El organizador ya no existe y sigues `ADMIN`.

### b) CLAIM aprobada → el dueño no la puede borrar

Necesita un organizador verificado y sin dueño. Si *Salsa Loca* (`88888888-…`) ya tiene dueño (por
ejemplo, Ana de la §8), crea otro con `POST /rest/admin/organizers/unclaimed` (ya sale verificado) y usa
su id como `targetOrganizerId`, o recarga el seed.

```
POST http://localhost:8081/rest/organizer-requests
Authorization: Bearer <tu token>
Content-Type: application/json

{
  "type": "CLAIM",
  "targetOrganizerId": "88888888-8888-8888-8888-888888888888",
  "instagram": "salsaloca.milano",
  "contactEmail": "titolare@salsaloca.it",
  "message": "Prova D13: sono il titolare.",
  "termsAccepted": true
}
```

```
PATCH http://localhost:8081/rest/admin/organizer-requests/<request_id>/approve
Authorization: Bearer <token admin>
```

**Esperado**: `200`. Eres el dueño de *Salsa Loca*.

```
DELETE http://localhost:8081/rest/organizers/88888888-8888-8888-8888-888888888888
Authorization: Bearer <tu token>
```

**Esperado**: `403` con `message: "This organizer is managed by BallaStasera: only an admin can delete it"`.
*Salsa Loca* sigue existiendo (`SELECT user_id FROM organizers WHERE id = '88888888-…'` → tu id) y su venue
`99999999-…` sigue enlazado.

Para terminar, el admin sí la borra. Ojo con la URL: es `/rest/admin/organizers/…`, no `/rest/organizers/…`
(con esa da `403`):

```
DELETE http://localhost:8081/rest/admin/organizers/88888888-8888-8888-8888-888888888888
Authorization: Bearer <token admin>
```

**Esperado**: `204`. Si vas a seguir usando *Salsa Loca*, recarga el seed.

### c) Asignada a mano por el admin → el dueño no la puede borrar

*Este caso desaparece con D10: `PATCH /rest/admin/organizers/{id}/claim` se borra (decidido el 29/10). Quitarlo
entonces.*

```
POST http://localhost:8081/rest/admin/organizers/unclaimed
Authorization: Bearer <token admin>
Content-Type: application/json

{ "name": "Prova D13 Claim", "type": "PERSON" }
```

**Esperado**: `201`. Copia el `id` (`<organizer_id>`).

```
PATCH http://localhost:8081/rest/admin/organizers/<organizer_id>/claim
Authorization: Bearer <token admin>
Content-Type: application/json

{ "userId": "<tu id>" }
```

```
DELETE http://localhost:8081/rest/organizers/<organizer_id>
Authorization: Bearer <tu token>
```

**Esperado**: `403`, el mismo mensaje que en b). Limpia con
`DELETE http://localhost:8081/rest/admin/organizers/<organizer_id>` → `204`, y sigues `ADMIN`.

### d) No es tuyo → el 403 de siempre

`DELETE http://localhost:8081/rest/organizers/11111111-1111-1111-1111-111111111111` (*Tropical Milano*, del
usuario `22222222-…`) con tu token → `403` con `message: "You are not owner of this organizer"`. Va antes
que D13: si no eres el dueño, ni siquiera se mira la solicitud.

---

## 11. D13: soltar un organizador (`release` del dueño y `unclaim` del admin)

Plan: `organizer-requests-plan.md` (fase 3, D13 ampliada). Soltar le quita el dueño **sin borrar nada**
(`user = null`, `claimed = false`). El organizador, su venue y sus eventos siguen igual, vuelve a salir en
`claimable` y la CLAIM APPROVED se queda como historial. Lo que el dueño creó con una NEW nunca se suelta,
solo se borra (§10).

- `PATCH /rest/organizers/{id}/release`: el dueño, solo si le llegó por CLAIM o se lo asignó el admin.
  `204`, sin email.
- `PATCH /rest/admin/organizers/{id}/unclaim`: el admin, cualquiera con dueño menos los de una NEW. `200`
  con `OrganizerDetailDto`, y el antiguo dueño recibe el email «Il profilo organizzatore non è più associato
  al tuo account.» después del commit.

**✅ Probado el 31/10** (casos a–f, código en `fb8894d`), con una cuenta USER y otra ADMIN: incluye el
`unclaim` sin ser ADMIN → `403` del final.

Con tu cuenta, como en la §10 (`<tu token>` y `<token admin>` son el mismo). Como eres `ADMIN`, sigues
`ADMIN` después de soltar (`demoteToUser` solo baja a un `ORGANIZER`), y el email de `unclaim` te llega a ti.
Antes de cada caso, sin organizadores ni solicitudes PENDING (las mismas SQL de la §10).

### a) CLAIM aprobada → el dueño la suelta

Haz la CLAIM sobre *Salsa Loca* y apruébala como en la §10 b) (si ya tiene dueño, recarga el seed).

```
PATCH http://localhost:8081/rest/organizers/88888888-8888-8888-8888-888888888888/release
Authorization: Bearer <tu token>
```

**Esperado**: `204`. Comprueba:

```sql
SELECT user_id, claimed FROM organizers WHERE id = '88888888-8888-8888-8888-888888888888';      -- null, false
SELECT organizer_id FROM venues WHERE id = '99999999-9999-9999-9999-999999999999';              -- sigue 88888888-…
SELECT type, status FROM organizer_requests WHERE user_id = '<tu id>' ORDER BY created_at DESC; -- CLAIM APPROVED
```

`GET http://localhost:8081/rest/organizers/claimable?name=salsa` (sin token) → *Salsa Loca* vuelve a salir.
`GET http://localhost:8081/rest/organizers/me` → `[]`. No llega ningún email.

Un segundo `release` → `403` con `message: "You are not owner of this organizer"`: ya no es tuyo.

### b) NEW aprobada → el dueño no la puede soltar

Crea y aprueba una NEW como en la §10 a) (`<organizer_id>`).

```
PATCH http://localhost:8081/rest/organizers/<organizer_id>/release
Authorization: Bearer <tu token>
```

**Esperado**: `403` con `message: "You created this organizer: delete it instead of releasing it"`. Sigues
siendo el dueño (`SELECT user_id FROM organizers WHERE id = '<organizer_id>'` → tu id). Déjalo así para el
caso e).

### c) No es tuyo → el 403 de siempre

`PATCH http://localhost:8081/rest/organizers/11111111-1111-1111-1111-111111111111/release` (*Tropical
Milano*, del usuario `22222222-…`) con tu token → `403` con `message: "You are not owner of this
organizer"`. Va antes que D13: si no eres el dueño, ni siquiera se mira la solicitud.

### d) `unclaim` de una CLAIM aprobada

Después del caso e) (sin organizadores), vuelve a hacer la CLAIM sobre *Salsa Loca* y apruébala: tras el
caso a) está libre otra vez.

```
PATCH http://localhost:8081/rest/admin/organizers/88888888-8888-8888-8888-888888888888/unclaim
Authorization: Bearer <token admin>
```

**Esperado**: `200` con el organizador y `"claimed": false`. Las mismas SQL del caso a). Te llega el email
«Il profilo organizzatore non è più associato al tuo account.» con el nombre *Salsa Loca*.

Repetir el mismo `unclaim` → `409` con `errors.code = "ORGANIZER_NOT_CLAIMED"` (caso f).

### e) `unclaim` de una NEW aprobada → 409, no cambia nada

Con el organizador del caso b):

```
PATCH http://localhost:8081/rest/admin/organizers/<organizer_id>/unclaim
Authorization: Bearer <token admin>
```

**Esperado**: `409` con `errors.code = "ORGANIZER_CREATED_BY_OWNER"`. Sigues siendo el dueño y no llega
ningún email. Limpia con `DELETE http://localhost:8081/rest/admin/organizers/<organizer_id>` → `204`.

### f) `unclaim` sin dueño y organizador que no existe

- Un organizador sin dueño, por ejemplo *Salsa Loca* después del caso d) o uno recién creado con
  `POST /rest/admin/organizers/unclaimed` → `409` con `errors.code = "ORGANIZER_NOT_CLAIMED"`.
- `PATCH http://localhost:8081/rest/admin/organizers/00000000-0000-0000-0000-000000000000/unclaim` → `404`
  con `message: "Organizer not found with id 00000000-…"`.

### g) `unclaim` sin ser ADMIN

El mismo `PATCH .../unclaim` con el token de una cuenta USER → `403` (como en la §8, «Sin ser ADMIN»).

---

## 12. Series sin organizador y eventos retirados al borrar (B, pasos 1–3)

Plan: `organizer-requests-plan.md` (fase 3, D13: retirar los eventos futuros al borrar, paso 1). Cuando se
borra un organizador, `ON DELETE SET NULL` deja `organizer_id` a null en sus series. Antes:
- `GET /rest/event-series/{id}` daba `404`, porque `findByIdWithDetails` hacía un `JOIN FETCH` normal
  sobre el organizador.
- `PATCH`/`DELETE` de la serie daban `500` (NPE en `assertOwnership`).

Ahora la query usa `LEFT JOIN FETCH`, como `EventsRepository`, y hay null checks en `buildDetailDto` y
`assertOwnership`.

**✅ Probado el 31/10.**

### a) Crear la serie como admin

`cityId`: `SELECT id FROM cities WHERE slug = 'milano';`

```
POST http://localhost:8081/rest/admin/event-series
Authorization: Bearer <token admin>
Content-Type: application/json
```

```json
{
  "organizerId": "88888888-8888-8888-8888-888888888888",
  "venueId": "99999999-9999-9999-9999-999999999999",
  "cityId": <id de milano>,
  "title": "Social del venerdì",
  "recurrenceDays": ["FRIDAY"],
  "description": "Serie de prueba para el organizador null.",
  "address": "Via Borsieri 12",
  "latitude": 45.4889,
  "longitude": 9.1890,
  "startTime": "21:30:00",
  "endTime": "02:00:00",
  "danceStyleIds": []
}
```

**Esperado**: `201`. Apunta el `id` (`<series_id>`). Las coordenadas evitan el geocoding. Sin
`instagramUrl`, para que se vea el fallback.

`GET http://localhost:8081/rest/event-series/<series_id>` (sin token) → `200` con
`"instagramUrl": "salsaloca.milano"` (el del organizador).

### b) Sin organizador

```sql
UPDATE event_series SET organizer_id = NULL WHERE id = '<series_id>';
```

- `GET http://localhost:8081/rest/event-series/<series_id>` (sin token) → `200` con `"instagramUrl": null`.
- `PATCH http://localhost:8081/rest/event-series/<series_id>` con el token de una cuenta USER y
  `{"title": "Cambio"}` → `403` con `message: "Not the owner of this event series"`.
- `DELETE http://localhost:8081/rest/event-series/<series_id>` con el mismo token → el mismo `403`.

Limpia con `DELETE FROM event_series WHERE id = '<series_id>';` (sin ocurrencias generadas, no hay eventos).

### c) Borrar el organizador retira sus eventos futuros (B, pasos 2 y 3)

Al borrar un organizador (`delete` del dueño y `deleteAsAdmin`), `retireUpcomingEvents` pasa sus eventos
futuros a `CANCELLED` y desactiva sus series **antes** del `DELETE` (después `organizer_id` ya es null). Los
pasados y los que están en curso no se tocan. `release`/`unclaim` no tocan los eventos.

**✅ Probado el 31/10** con `deleteAsAdmin` sobre *Salsa Loca* (puerto 8080 ese día).

1. Crea la serie como en el caso a) y genera ocurrencias (hoy sábado 31/10: 3 viernes pasados y 4 futuros):

   ```
   POST http://localhost:8081/rest/admin/event-series/<series_id>/occurrences
   Authorization: Bearer <token admin>
   Content-Type: application/json
   ```

   ```json
   { "startDate": "2026-10-16", "endDate": "2026-11-27" }
   ```

   **Esperado**: `201` con 7 eventos.

2. Antes de borrar:

   ```sql
   SELECT start_at::date AS dia, status, organizer_id, updated_at
   FROM events WHERE series_id = '<series_id>' ORDER BY start_at;          -- 7 PUBLISHED, organizer 88888888-…
   SELECT active, organizer_id, updated_at FROM event_series WHERE id = '<series_id>';  -- true
   ```

3. `DELETE http://localhost:8081/rest/admin/organizers/88888888-8888-8888-8888-888888888888` con
   `<token admin>` → `204`. Después:

   ```sql
   SELECT start_at::date AS dia, status, organizer_id, updated_at
   FROM events WHERE series_id = '<series_id>' ORDER BY start_at;
   -- 16/10, 23/10, 30/10 → PUBLISHED, updated_at sin cambios
   -- 06/11 … 27/11 → CANCELLED, updated_at de ahora
   -- organizer_id → NULL en las 7 (ON DELETE SET NULL)
   SELECT active, organizer_id, updated_at FROM event_series WHERE id = '<series_id>';  -- false, NULL, de ahora
   ```

4. `GET http://localhost:8081/rest/event-series/<series_id>` (sin token) → `200`. Generar ocurrencias otra vez
   (`"endDate": "2026-12-31"`) → error de `EventSeriesInactiveException`.

Limpieza: borra la prueba y restaura *Salsa Loca* (el `INSERT` es el del seed, `seed_test_data.sql`):

```sql
DELETE FROM events WHERE series_id = '<series_id>';
DELETE FROM event_series WHERE id = '<series_id>';

INSERT INTO organizers (id, user_id, name, slug, type, description, contact_email, instagram, is_verified, claimed)
VALUES ('88888888-8888-8888-8888-888888888888', NULL,
        'Salsa Loca Milano', 'salsa-loca-milano', 'SCHOOL',
        'Scuola di salsa e bachata in zona Isola. Corsi tutte le sere, social il venerdì.',
        'info@salsaloca.it', 'salsaloca.milano', true, false);

UPDATE venues SET organizer_id = '88888888-8888-8888-8888-888888888888'
WHERE id = '99999999-9999-9999-9999-999999999999';
```
