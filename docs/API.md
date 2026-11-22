# BallaStasera — API y arquitectura (estado actual)

Plataforma de eventos de baile latino/social (salsa, bachata, kizomba...) con mapa en vivo.
Este documento resume lo implementado hasta ahora: autenticación, mapa de eventos, asistencia social
y el modelo de privacidad para el Instagram de los usuarios.

Stack: Spring Boot, PostgreSQL, Spring Security (OAuth2 Google + JWT stateless), Lombok, MapStruct.

---

## 1. Seguridad

`SecurityConfig` — sesiones **stateless** (JWT), login vía Google OAuth2.

| Regla | Alcance |
|---|---|
| `permitAll` | `/oauth2/**`, `/login/**` |
| `permitAll` (solo `GET`) | `/api/events`, `/api/events/{id}`, `/api/events/{id}/attendees`, `/api/cities/**`, `/api/organizers/**`, `/api/dance-styles/**` |
| `authenticated` | todo lo demás (`POST`/`PATCH`/`DELETE` en `/api/**`, y `/auth/me`) |

Consecuencia: **consultar el mapa y el detalle de un evento nunca requiere login**; publicar, marcar
asistencia o editar el perfil sí.

`BackendErrorResponse` traduce `EntityNotFoundException` → `404` y `AccessDeniedException` → `403`
con un `ErrorDetails` JSON en vez del `500` por defecto de Spring.

### Rate limit

`RateLimitFilter` (en la cadena de Security, después del JWT) limita las requests con Bucket4j sobre el
mismo Redis de la cache. Cuenta por usuario si hay login y por IP si no. Pasado el límite responde:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 12
Content-Type: application/json

{ "timestamp": "...", "message": "Too many requests", "path": "/rest/events",
  "details": "Rate limit exceeded, retry in 12 seconds", "status": "TOO_MANY_REQUESTS",
  "errors": { "code": "RATE_LIMITED" } }
```

| Grupo | Qué cuenta | Límite (default) |
|---|---|---|
| `READ` | `GET` y `HEAD` | 120/min |
| `WRITE` | el resto de `POST`/`PATCH`/`PUT`/`DELETE` | 60/min |
| `AUTH` | `/oauth2/**`, `/login/**` (siempre por IP) | 20/min |
| `SOCIAL` | escribir en `/rest/users/{id}/follow`, `/block`, `/report`, `/invites` | 30/min y 300/día |
| `UPLOAD` | requests multipart (flyer, logo, avatar) | 10/hora |

- No cuentan los `OPTIONS` (preflight de CORS) ni `/rest/health`.
- Los límites se cambian con variables de entorno (`RATE_LIMIT_READ_PER_MINUTE`, etc., en
  `application.properties`), y `RATE_LIMIT_ENABLED=false` lo apaga.
- Si Redis no responde, la request pasa: un Redis caído no tumba la API.
- `Retry-After` no está en los headers expuestos por CORS: el FE ve el `429` y el `code`, no los segundos.

---

## 2. Modelo de datos (resumen relevante para la API)

Tablas ya existentes: `cities`, `users`, `organizers`, `venues`, `dance_styles`, `event_series`,
`events`, `event_dance_styles`, `favorites`, `event_attendance`.

Cambios añadidos en este bloque de trabajo:

```sql
ALTER TABLE users  ADD COLUMN instagram            TEXT;
ALTER TABLE users  ADD COLUMN show_profile_public   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE events ADD COLUMN instagram_url         TEXT;
ALTER TABLE events ADD COLUMN whatsapp_url          TEXT;
CREATE INDEX idx_events_status_end ON events(status, end_at);
```

**Por qué `show_profile_public` es opt-in y no automático**: decir "voy" y mostrar cara + Instagram en
una lista pública son dos cosas distintas. El contador de asistentes (`goingCount`) siempre cuenta a
todos; la lista de `attendees` con nombre/IG solo incluye a quien activó el toggle. Así el número
social es real sin exponer a nadie por defecto.

**Por qué `events.instagram_url`/`whatsapp_url` son opcionales**: un organizador puede tener grupos de
Instagram/WhatsApp distintos por evento. Si el evento no define el suyo, la API cae al Instagram del
organizador (`organizer.instagram`) como fallback — resuelto en el backend, no en el frontend.

---

## 3. Cálculo de "en vivo" (sin campo nuevo en BD)

Un evento se considera **en vivo** (`liveNow = true`) cuando:

```
start_at <= now() < COALESCE(end_at, start_at + 4 horas)
```

Las 4 horas son el valor por defecto cuando el organizador no puso `end_at`. Este cálculo se hace
**siempre en el servidor** (`EventTimingUtils.isLiveNow`), nunca se confía en el reloj del cliente.

La query del mapa (`EventsRepository.findActiveOrUpcomingIdsInBounds`) usa la misma condición para
**excluir eventos pasados directamente en SQL** — el mapa nunca recibe puntos "muertos" que haya que
filtrar o pintar en gris en el frontend.

---

## 4. Endpoints

### `GET /api/cities` — público

Lista las ciudades activas disponibles para filtros y navegación del catálogo. La respuesta se ordena
alfabéticamente por `name`.

**Respuesta** — `List<CityDto>`:

```json
[
  {
    "id": 1,
    "name": "Milano",
    "province": "MI",
    "region": "Lombardia",
    "country": "IT",
    "latitude": 45.4642,
    "longitude": 9.1900,
    "slug": "milano",
    "isActive": true
  }
]
```

### `GET /api/cities/{id}` — público

Devuelve una ciudad activa por su identificador.

`404` si la ciudad no existe o está inactiva.

---

### `GET /api/dance-styles` — público

Lista los estilos de baile disponibles para filtros y clasificación de eventos. La respuesta se ordena
alfabéticamente por `name`.

**Respuesta** — `List<DanceStyleDto>`:

```json
[
  {
    "id": 1,
    "name": "Bachata",
    "slug": "bachata"
  }
]
```

### `GET /api/dance-styles/{id}` — público

Devuelve un estilo de baile por su identificador.

`404` si el estilo de baile no existe.

---

### `GET /api/events` — público

Marcadores del mapa: eventos publicados, en vivo o por empezar, dentro del viewport visible. Nunca
devuelve eventos pasados (se filtran en la query, no en el frontend).

**Query params**

| Param | Tipo | Requerido | Descripción |
|---|---|---|---|
| `minLat`, `maxLat`, `minLng`, `maxLng` | `double` | sí | bounding box del mapa visible |
| `cityId` | `long` | no | filtra por ciudad |

**Respuesta** — `List<EventCardDto>`:

```json
[
  {
    "id": "uuid",
    "slug": "salsa-al-tropical",
    "title": "Salsa al Tropical",
    "flyerUrl": "https://...",
    "startAt": "2026-08-10T22:00:00+02:00",
    "endAt": "2026-08-11T02:00:00+02:00",
    "liveNow": true,
    "free": false,
    "price": 10.00,
    "currency": "EUR",
    "latitude": 45.4668,
    "longitude": 9.1905,
    "address": "Via Tortona 12, Milano",
    "organizer": {
      "id": "uuid", "name": "Tropical Milano", "slug": "tropical-milano",
      "logoUrl": "https://...", "verified": true
    },
    "venueName": "Sala Havana",
    "danceStyles": ["Bachata", "Salsa"],
    "goingCount": 42
  }
]
```

Nota de rendimiento: la query de ids (nativa, con bounding box) y la carga de detalle
(`JOIN FETCH` de organizer/venue/danceStyles) van separadas a propósito para evitar N+1; los conteos
de "van" se traen en un solo `GROUP BY` por lote, no una query por evento.

---

### `GET /api/events/{id}` — público

Detalle completo de un evento.

**Respuesta** — `EventDetailDto`: todo lo del card, más `description`, `cityName`, `organizer`
completo (`OrganizerDetailDto`: website, phone, contactEmail, facebook, instagram...),
`instagramUrl`/`whatsappUrl` **ya resueltos con el fallback al organizador**, y
`goingCount`/`interestedCount` por separado.

`404` si el evento no existe.

---

### Mutaciones de Events — requieren login y ownership

Las mutaciones requieren un usuario autenticado. El servicio comprueba que el organizer del evento
pertenezca al usuario autenticado (`event.organizer.user.id`). Un usuario anónimo recibe `401` y un
usuario autenticado sin permisos recibe `403`.

### `POST /api/events` — crear evento

El `organizerId` del body debe pertenecer al usuario autenticado y el organizer debe estar verificado.
El evento se guarda inicialmente con estado `PENDING`.

`cityId`, `title`, `startAt`, `endAt` y `address` son obligatorios. `venueId` y `seriesId` son
opcionales. Un venue sin organizer puede compartirse; un venue o una serie perteneciente a otro
organizer se rechaza con `403`.

`201 Created` con `EventDetailDto`. El DTO de respuesta actual no expone el campo `status`.
`400` para datos inválidos, `403` para ownership/verification/asociaciones ajenas y `404` para
organizer, ciudad, venue o serie inexistentes.

### `PATCH /api/events/{id}` — editar evento propio

Todos los campos del `EventUpdateDto` son opcionales. Solo el organizer propietario puede editar el
evento; no se puede cambiar su organizer ni su status desde este endpoint. Las asociaciones `venueId`
y `seriesId` se vuelven a validar contra el organizer del evento.

`200 OK` con `EventDetailDto`. `400` para datos o rangos temporales inválidos, `403` si el usuario no
es propietario o intenta asociar un venue/serie ajeno y `404` si el evento o una asociación no existe.

### `PATCH /api/events/{id}/status` — cambiar estado

Solo el organizer propietario puede cambiar el estado. Los valores válidos son `DRAFT`, `PENDING`,
`PUBLISHED` y `CANCELLED`; actualmente no hay una matriz adicional de transiciones.

`200 OK` con `EventDetailDto`. El DTO de respuesta actual no expone el campo `status`.

### `DELETE /api/events/{id}` — eliminar evento propio

Solo el organizer propietario puede eliminar el evento. Devuelve `204 No Content`, `403` si el usuario
no es propietario y `404` si el evento no existe.

---

### `GET /api/events/{id}/attendees` — público

Lista paginada de quienes marcaron **GOING** y activaron `show_profile_public`. Es el endpoint que
alimenta las "caras con Instagram" del popup — separado del detalle a propósito, para no inflar la
card del mapa ni el detalle con datos de usuarios.

**Query params**: `page` (default `0`), `size` (default `20`).

**Respuesta** — `Page<AttendeeDto>`, cada item con `userId`, `displayName`, `avatarUrl`, `instagram`,
`lookingForPartner` («Cerco qualcuno con cui ballare» en esta serata) y `relation` (con login, la relación
de quien mira con esa persona; `null` sin login).

`404` si el evento no existe.

---

### `POST /api/events/{id}/attendance` — requiere login

Marca «Ci vado». Idempotente: si ya iba, no suma otra vez a `goingCount`.

**Body** opcional:

```json
{ "lookingForPartner": true }
```

- Sin body o con `lookingForPartner: null`: la marca no cambia (`false` al crear, la que tenía si ya iba).
- Con `true`/`false`: la pone o la quita, vaya ya o no.

`204 No Content` en éxito. `404` si el evento no existe.

### `GET /api/events/{id}/attendance/me` — requiere login

`{ "going": true, "lookingForPartner": false }`: si el usuario dijo «Ci vado» y si marcó «Cerco qualcuno».
`GET /events/{id}/going` sigue devolviendo solo `true`/`false`.

### `GET /api/events/{id}/friends-going` — requiere login

`List<UserSummaryDto>` con los amigos de quien mira que van (`relation = MUTUAL`), para «Sara e Luca ci
vanno». Mismo filtro que «Chi ci va»; no salen los que eligieron que nadie vea sus serate. Fuera de la cache
del detalle porque depende de quien mira. `404` si el evento no existe.

---

### `DELETE /api/events/{id}/attendance` — requiere login

Quita al usuario logueado de la lista de asistentes del evento (deja de ir / ya no le interesa).
`204 No Content`.

---

### `POST /api/organizers` — requiere login

El propio usuario se postula como organizador. Queda `isVerified=false` hasta que un admin lo
apruebe; solo entonces puede publicar eventos.

**Body** — `OrganizerCreateDto`: `name`, `type`, `description`, `logoUrl`, `website`, `phone`,
`contactEmail`, `instagram`, `facebook`.

`201 Created` con `OrganizerDetailDto`.

---

### `GET /api/organizers` — público

Directorio de organizadores. Solo devuelve los **ya verificados** (`isVerified=true`) — los
pendientes de aprobación no se exponen públicamente.

**Query params**: `page` (default `0`), `size` (default `20`).

**Respuesta** — `Page<OrganizerSummaryDto>`.

---

### `GET /api/organizers/{slug}` — público

Perfil público de un organizador (la página que ve cualquier visitante, no requiere login).

**Respuesta** — `OrganizerDetailDto`. `404` si no existe.

---

### `GET /api/organizers/me` — requiere login

Devuelve el/los perfil(es) de organizador del usuario logueado (un usuario puede tener más de uno).
Pensado para el panel/dashboard del propio organizador, no para consulta pública.

**Respuesta** — `List<OrganizerDetailDto>`.

---

### `PATCH /api/organizers/{id}` — requiere login (solo el dueño)

El organizador edita su propio perfil (nombre, descripción, logo, contacto, redes). El `slug` no
cambia aunque cambie el `name`, para no romper links ya compartidos.

**Autorización**: se valida que `organizer.user.id == principal.id`; si no coincide, `403`. No es un
endpoint de admin — el admin usa `/api/admin/organizers/**` para verificar, no para editar el
contenido del perfil.

**Body** — `OrganizerUpdateDto` (igual a `OrganizerCreateDto` sin `type`).

**Respuesta** — `OrganizerDetailDto`.

---

### `GET /auth/me` — requiere login

El perfil completo del usuario logueado (`UserDto`): `userId`, `email`, `displayName`, `role`,
`avatarUrl`, `instagram`, `showProfilePublic`, `loginCount` y, desde el 16/11/2026 (perfil ampliado,
`user-profile-plan.md`):

| Campo | Tipo | Notas |
|---|---|---|
| `whatsapp` | string \| null | E.164: `+393331234567` |
| `whatsappVisibility` | `NOBODY` \| `EVERYONE` | Default `EVERYONE` |
| `birthDate` | `YYYY-MM-DD` \| null | Solo el dueño la ve; el perfil público da la edad |
| `gender` | `MALE` \| `FEMALE` \| `OTHER` \| `UNDISCLOSED` \| null | |
| `danceRole` | `LEADER` \| `FOLLOWER` \| `BOTH` \| null | |
| `danceStyles` | `[{ danceStyleId, name, slug, level }]` | `level`: `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` \| `TEACHER` |

---

### `PATCH /auth/me` — requiere login

Guarda el formulario del perfil. **Reemplaza el formulario completo**: el front manda siempre todos los
campos; uno que no viene queda en `null` (así se puede vaciar cualquier campo). Para cambiar un solo
campo, se manda el formulario entero con los valores actuales (los de `GET /auth/me`).

**Body** — `UserUpdateDto`:

```json
{
  "instagram": "@ana.baila",
  "showProfilePublic": true,
  "whatsapp": "+393331234567",
  "whatsappVisibility": "EVERYONE",
  "birthDate": "1995-04-12",
  "gender": "FEMALE",
  "danceRole": "FOLLOWER",
  "danceStyles": [
    { "danceStyleId": 1, "level": "ADVANCED" },
    { "danceStyleId": 2, "level": "BEGINNER" }
  ],
  "bio": "Bachata e salsa, sempre in pista",
  "attendanceVisibility": "EVERYONE",
  "schoolOrganizerId": "…",
  "schoolName": null,
  "originCountry": "CO",
  "homeCity": { "name": "Milano", "region": "Lombardia", "country": "IT", "latitude": 45.46, "longitude": 9.19 },
  "showHomeCity": false
}
```

- `showProfilePublic` es obligatorio.
- `instagram` acepta con o sin `@` inicial (se normaliza); `""` o `null` lo vacía.
- `whatsapp` en formato E.164 (`+` y prefijo de país, sin espacios); `""` o `null` lo vacía.
- `whatsappVisibility` `null` se guarda como `MUTUALS`.
- `bio`: máx. 150 caracteres; se recortan los espacios, `""` o `null` la vacía.
- `attendanceVisibility` (quién ve las serate a las que vas): `EVERYONE`, `MUTUALS` (solo amigos) o
  `NOBODY`. `null` se guarda como `EVERYONE`.
- Escuela: `schoolOrganizerId` tiene que ser un organizer **SCHOOL verificado** (si no → `404`); si viene,
  `schoolName` se ignora. Si la escuela no está en la app, `schoolName` (máx. 80) con texto libre.
- `originCountry`: ISO 3166-1 alpha-2 en mayúsculas (`CO`, `VE`, `IT`).
- `homeCity`: el objeto que devuelve Photon en el FE, ya normalizado; `null` = sin ciudad. `name` y
  `country` (alpha-2) obligatorios, lat/lng en rango.
- `showHomeCity` («Mostra la città nel profilo»): `null` se guarda como `false`.
- `birthDate`: edad entre 16 y 100 años.
- `danceStyles`: máximo 10, `level` obligatorio en cada uno. `null` o `[]` borra todos. Si un estilo se
  repite, gana el último. Un `danceStyleId` que no existe → `404`.
- Errores de validación → `400` con `errors` por campo: `instagram`, `whatsapp`, `showProfilePublic`,
  `birthDateValid` (edad), `danceStyles`, `danceStyles[0].level`. Los mensajes son técnicos, en inglés:
  el front muestra sus propios textos según la clave.

Devuelve el mismo shape que `GET /auth/me` con los valores actualizados.

---

### `GET /users/{id}` — requiere login

Perfil de otro usuario (`UserProfileDto`), por ejemplo al tocarlo en la lista de «parteciperò»
(`GET /events/{id}/attendees`, que ya solo muestra perfiles públicos, no borrados y no baneados).

```json
{
  "userId": "…",
  "displayName": "Ana",
  "avatarUrl": "https://…",
  "instagram": "ana.baila",
  "age": 31,
  "gender": "FEMALE",
  "danceRole": "FOLLOWER",
  "whatsapp": "+393331234567",
  "danceStyles": [{ "danceStyleId": 1, "name": "Salsa", "slug": "salsa", "level": "ADVANCED" }],
  "relation": "MUTUAL",
  "muted": false,
  "followersCount": 12,
  "followingCount": 7,
  "eventsCount": 30
}
```

- `age` en vez de la fecha de nacimiento.
- `whatsapp`: el dueño siempre ve el suyo. Los demás, según `whatsappVisibility`: `EVERYONE` todos, `MUTUALS`
  solo los amigos (`relation = MUTUAL`), `NOBODY` nadie. Con menos de 18 años no lo ve nadie más.
- `relation` de quien mira con este usuario: `NONE` «Segui», `FOLLOWING` «Segui già», `FOLLOWED_BY`
  «Ricambia», `MUTUAL` «Amici». `NONE` si es el propio perfil.
- `eventsCount` = serate pasadas a las que dijo «Ci vado».
- `404` si el perfil es privado (`showProfilePublic = false`), está borrado, baneado o hay un bloqueo en
  cualquier sentido, salvo que lo pida el propio usuario. Mismo `404` en todos los casos: no se revela el bloqueo.
- Sin token → `401`.

Desde la fase 3 trae «Avete in comune» y el pallino, en campos planos:
- `mutualFriends` (hasta 3 `UserSummaryDto`, en orden alfabético, `relation = MUTUAL`) y `mutualFriendsCount`.
- `eventsTogetherCount` (serate ya pasadas a las que fueron los dos), `lastEventTogetherId`,
  `lastEventTogetherTitle`, `lastEventTogetherStartAt`. `0`/`null` si quien mira no puede ver sus serate.
- `lookingForPartnerToday`: marcó «Cerco qualcuno» en una serata en curso o de las próximas 24 horas. Solo si
  quien mira puede ver sus serate.
- En el propio perfil, los amigos en común y las serate juntos vienen vacíos.

### `GET /users/{id}/attendance?past=&cursorStartAt=&cursorId=&limit=` — requiere login

«Le sue prossime serate» (`past=true`: las pasadas). Mismo orden, cursor y `CursorPage<EventCardDto>` que
`/users/me/attendance`. `404` si el perfil no se puede ver. Si eligió que quien mira no vea sus serate
(`attendanceVisibility`), lista vacía con `total = 0`.

Desde la fase 2 también trae `bio`, `schoolOrganizerId`/`schoolName`/`schoolSlug` (enlazada: id, nombre y
slug del organizer; escrita a mano: solo `schoolName`), `originCountry` y `homeCityName` (solo el nombre, y
solo si el dueño activó `showHomeCity`; nunca región ni coordenadas).

`GET /auth/me` y la respuesta de `PATCH /auth/me` suman `followersCount`, `followingCount` y `eventsCount`,
y los campos de la fase 2 tal cual: `bio`, `attendanceVisibility`, la escuela, `originCountry`, `homeCity`
(el objeto completo), `showHomeCity`, `avatarSource` (`GOOGLE`/`CUSTOM`/`NONE`) y `googleAvatarUrl` (la de
Google aunque se muestre otra, para la opción «Usa la foto di Google»).
`whatsappVisibility` admite `MUTUALS`, que es el valor por defecto de los usuarios nuevos y de un `null`.

### Foto de perfil — requiere login

| Endpoint | Respuesta | Notas |
|---|---|---|
| `PATCH /users/me/avatar` (multipart, parte `avatar`) | `UserDto` | Sube o cambia la foto propia: máx. 5 MB (`413`), JPG/PNG/WebP (`415`). Se guarda en webp (lado largo 512 px) y pasa a `avatarSource = CUSTOM` |
| `PATCH /users/me/avatar/source` `{ "source": "GOOGLE" \| "NONE" }` | `UserDto` | «Usa la foto di Google» o «Nessuna foto». `CUSTOM` → `400` (se sube con el multipart). La foto propia se borra del bucket |

- `avatarUrl` (en todos los DTOs) es siempre la foto que se muestra: la de Google, la propia o `null`.
- El login con Google ya no pisa una foto propia: guarda la de Google en `googleAvatarUrl` y solo la
  muestra si `avatarSource = GOOGLE`.

### `GET /organizers/schools?name=&limit=` — público

Autocompletado de «Scuola o crew»: organizers `SCHOOL` verificados cuyo nombre contiene `name` (mín. 2
caracteres), en orden alfabético. `limit` 1–10 (default 5). Devuelve `List<OrganizerSummaryDto>`.

### Quién ve tus serate

`GET /events/{id}/attendees` («Chi ci va») respeta `attendanceVisibility`: `EVERYONE` sale para todos,
`MUTUALS` solo para sus amigos, `NOBODY` para nadie. Cada uno se ve siempre a sí mismo. El número de los que
van (`goingCount`) los cuenta a todos igual. Las caritas del mapa y las cards solo muestran a quien tiene
`EVERYONE`.

### Seguir y bloquear — requieren login

| Endpoint | Respuesta | Notas |
|---|---|---|
| `POST /users/{id}/follow` | `204` | Idempotente. `400` con `code: SELF_ACTION` si es uno mismo. `404` si el perfil es privado, borrado, baneado o hay un bloqueo |
| `DELETE /users/{id}/follow` | `204` | Idempotente. Sin chequeos: se puede dejar de seguir a quien se volvió privado |
| `POST /users/{id}/block` | `204` | Idempotente. Borra los follows en los dos sentidos. `400 SELF_ACTION` si es uno mismo; `404` si no existe o está borrado. Se puede bloquear un perfil privado |
| `DELETE /users/{id}/block` | `204` | Idempotente. Los follows borrados no vuelven |
| `GET /users/me/followers` | `CursorPage<UserSummaryDto>` | Quienes me siguen |
| `GET /users/me/following` | `CursorPage<UserSummaryDto>` | A quienes sigo |
| `GET /users/me/friends?q=` | `CursorPage<UserSummaryDto>` | Amigos (nos seguimos los dos). `q` busca en el nombre, sin distinguir mayúsculas, máx. 50 caracteres |
| `GET /users/me/blocks` | `List<UserSummaryDto>` | Personas que bloqueé, sin paginar |

```json
{ "userId": "…", "displayName": "Luca", "avatarUrl": "https://…", "relation": "MUTUAL", "profilePublic": true }
```

- `profilePublic = false`: perfil privado (por ejemplo, alguien privado que me sigue). Sale en la lista, pero sin
  enlace al perfil ni botón para seguirlo, porque los dos darían `404`: el FE muestra «Profilo privato». Dejar de
  seguir (`/me/following`) y desbloquear (`/me/blocks`) funcionan igual.

- Las tres listas paginadas: las más recientes primero, `limit` (default 20, máx. 50) y el cursor de la
  respuesta (`nextCursorStartAt` = fecha del follow, `nextCursorId` = id de la persona). `null` = no hay más.
- `total` = el total con el mismo filtro. No salen usuarios borrados ni baneados.
- `GET /events/{id}/attendees` («Chi ci va») con login no muestra a quien tenga un bloqueo con quien mira.

### Silenciar y denunciar — requieren login

| Endpoint | Respuesta | Notas |
|---|---|---|
| `PATCH /users/{id}/follow` `{ "muted": true \| false }` | `204` | «Silenzia» / «Riattiva» a quien sigo: sale de mi feed y de mi campanita (fases 5 y 6), y no se entera. `404` si no lo sigo; sin `muted` → `400`. Dejar de seguir pierde el silencio |
| `POST /users/{id}/report` `{ "reason", "note" }` | `204` | «Segnala». Va a la cola del admin y no oculta nada por sí sola. Idempotente mientras siga abierta (no crea otra). `400 SELF_ACTION` si es uno mismo; `404` si no existe o está borrado. Se puede denunciar un perfil privado o bloqueado |

- `reason`: `FAKE_PROFILE`, `HARASSMENT`, `INAPPROPRIATE_PHOTO` u `OTHER`. `note` máx. 500 caracteres,
  obligatoria con `OTHER` (`400`).
- `GET /users/{id}` suma `muted`: si quien mira lo silenció (menú ⋯). Solo puede ser `true` si lo sigue.
- «Segnala e blocca»: el FE hace las dos llamadas (`/report` y `/block`).

### Pestaña Amici — requieren login

| Endpoint | Respuesta | Notas |
|---|---|---|
| `GET /users/me/feed` | `FeedDto` | Attività: `tonight` («Stasera escono») y `news` («Novità»). Se calcula en cada request, sin cache |
| `GET /users/me/suggestions?limit=` | `List<SuggestionDto>` | Scopri, ya ordenadas. `limit` 1–20 (default 10) |

```json
{
  "tonight": [{ "eventId": "…", "eventSlug": "…", "eventTitle": "Serata salsa", "eventStartAt": "…",
                "venueName": "Tropical", "meGoing": true, "people": [UserSummaryDto] }],
  "news": [{ "type": "FOLLOWED_YOU", "at": "…", "user": UserSummaryDto, "eventId": null, "eventSlug": null,
             "eventTitle": null, "eventStartAt": null, "meGoing": false },
           { "type": "GOING_TO", "at": "…", "user": UserSummaryDto, "eventId": "…", "eventSlug": "…",
             "eventTitle": "Bachata Sunday", "eventStartAt": "…", "meGoing": false }]
}
```

- Las serate (`tonight` y `GOING_TO`): solo gente que sigo y no silencié, con perfil público, ni borrada ni
  baneada, y respetando su `attendanceVisibility` como «Chi ci va»: `MUTUALS` solo si somos amigos, `NOBODY`
  nunca.
- `FOLLOWED_YOU`: cualquiera que me siga, ni borrado ni baneado, salvo quien silencié. También los privados, con
  `profilePublic = false` (sin «Ricambia»).
- `tonight`: serate en curso o que empiezan antes de las próximas 06:00 hora de Italia, por hora de inicio. En
  cada una, la gente en el orden en que dijo «Ci vado» (`relation` `FOLLOWING` o `MUTUAL`). El subtítulo
  «Stasera a…» de la lista de amigos sale de aquí. Para «Stasera escono · 4 amici», el FE cuenta los `userId`
  distintos: quien va a dos serate esta noche sale en las dos.
- `news`, de los últimos 7 días y las más recientes primero (hasta 50 de cada tipo):
  - `FOLLOWED_YOU`: empezó a seguirme. `relation` `FOLLOWED_BY` («Ricambia», si `profilePublic`) o `MUTUAL`.
  - `GOING_TO`: dijo «Ci vado» a una serata de los próximos 7 días que no es de esta noche.
- `meGoing`: yo también voy («ci vai anche tu»).

```json
[{ "user": UserSummaryDto, "mutualFriendsCount": 3, "eventId": "…", "eventSlug": "…",
   "eventTitle": "Noche Latina", "eventStartAt": "…", "lookingForPartner": true }]
```

- Salen de dos sitios: gente que va a mis serate en curso o futuras y muestra sus serate a todos (`EVERYONE`), y
  amigos de mis amigos.
- Nunca: yo, a quien ya sigo, bloqueos en cualquier sentido, perfiles privados, borrados, baneados ni menores de 18
  (sin fecha de nacimiento cuenta como adulto).
- Orden: comparte serata conmigo, marcó «Cerco qualcuno» en una de ellas, más amigos en común.
- `user.relation` es `NONE` o `FOLLOWED_BY` («Ricambia»). `event*` es la serata más cercana que compartimos
  (prefiere una con «Cerco qualcuno»); `null` si no compartimos ninguna. El FE arma el «por qué» con estos campos.

### Moderación — solo ADMIN (`/rest/admin`)

| Endpoint | Respuesta | Notas |
|---|---|---|
| `GET /admin/reports?status=&page=&size=` | `Page<UserReportAdminDto>` | Cola por estado: `OPEN` (las más viejas primero), `DISMISSED` y `BANNED` (las últimas revisadas primero). `size` 1–50 |
| `PATCH /admin/reports/{id}` `{ "status": "DISMISSED" \| "BANNED" }` | `UserReportAdminDto` | «Ignora» cierra solo esta. «Banna» banea al denunciado y cierra como `BANNED` todas sus abiertas. `OPEN` → `400` |
| `PATCH /admin/users/{id}/ban` | `204` | Ban directo, sin denuncia. Idempotente. Cierra sus denuncias abiertas |
| `PATCH /admin/users/{id}/unban` | `204` | Idempotente. No reabre denuncias |
| `GET /admin/users/banned?page=&size=` | `Page<BannedUserAdminDto>` | «Utenti bannati», los últimos baneados primero (los anteriores a la fase 4, sin `bannedAt`, al final) |

```json
{
  "id": "…", "reason": "OTHER", "note": "Mi scrive di continuo", "status": "OPEN", "createdAt": "…",
  "reporterId": "…", "reporterName": "Ana", "reporterAvatarUrl": "https://…",
  "reportedId": "…", "reportedName": "Luca", "reportedAvatarUrl": "https://…", "reportedEmail": "luca@…",
  "reportedBanned": false, "openReportsAgainst": 2,
  "reviewedByName": null, "reviewedAt": null
}
```

- `409` con `code`: `REPORT_NOT_OPEN` (ya revisada), `CANNOT_BAN_ADMIN` (no se banea a un admin, ni a uno
  mismo), `CONCURRENT_UPDATE` (otro admin revisó la misma denuncia a la vez).
- Qué hace el ban: el login con Google lo rechaza y su JWT deja de valer en la siguiente request (`401` en las
  rutas que piden login; en las públicas entra como anónimo). Sale de todas partes (perfil, listas, «Chi ci
  va», contadores), pero sus follows no se borran: con el unban todo vuelve como estaba. Sigue contando en
  `goingCount`.

---

## 5. Capas y convenciones del código

- **Repositorios** (`EventsRepository`, `EventAttendanceRepository`): queries nativas solo donde hace
  falta sintaxis específica de Postgres (`COALESCE` + `INTERVAL` para bbox/en vivo); el resto en JPQL
  con `JOIN FETCH` explícito para evitar N+1.
- **DTOs**: clases (no records) con Lombok `@Getter/@Setter`, para poder usar `@Valid`/Bean Validation
  y MapStruct sin fricción.
- **Mappers** (`EventsMapper`, `AttendeeMapper`): MapStruct, `componentModel = "spring"`. Solo mapean
  campos que salen directo de la entidad; los campos calculados (`liveNow`, conteos, fallback de
  `instagramUrl`) se completan a mano en el service porque dependen de `now()` o de queries agregadas
  que no viven en la entidad `Events`.
- **Services**: interfaz (`manager/`) + implementación (`implementations/`), patrón ya existente en el
  proyecto, mantenido igual.
- **Errores**: `EntityNotFoundException` (JPA) capturada globalmente y traducida a `404`.

---

## 6. Pendiente (no implementado todavía)

- Restringir `/api/admin/organizers/**` a `ROLE_ADMIN` (hoy cualquier usuario autenticado puede
  llamarlo).
- Migraciones versionadas (Flyway/Liquibase) — hoy el schema se aplica a mano contra la BD real, sin
  registro de qué `ALTER TABLE` ya corrió en cada entorno.
