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
| `permitAll` (solo `GET`) | `/api/events/**`, `/api/cities/**`, `/api/venues/**`, `/api/organizers/**`, `/api/dance-styles/**` |
| `authenticated` | todo lo demás (`POST`/`PATCH`/`DELETE` en `/api/**`, y `/auth/me`) |

Consecuencia: **consultar el mapa y el detalle de un evento nunca requiere login**; publicar, marcar
asistencia o editar el perfil sí.

`GlobalExceptionHandler` traduce `EntityNotFoundException` → `404` con `{"error": "..."}` en vez del
`500` por defecto de Spring.

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
**siempre en el servidor** (`EventsServiceImpl.isLiveNow`), nunca se confía en el reloj del cliente.

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

### `GET /api/events/{id}/attendees` — público

Lista paginada de quienes marcaron **GOING** y activaron `show_profile_public`. Es el endpoint que
alimenta las "caras con Instagram" del popup — separado del detalle a propósito, para no inflar la
card del mapa ni el detalle con datos de usuarios.

**Query params**: `page` (default `0`), `size` (default `20`).

**Respuesta** — `Page<AttendeeDto>`, cada item con `userId`, `displayName`, `avatarUrl`, `instagram`.

`404` si el evento no existe.

---

### `POST /api/events/{id}/attendance` — requiere login

Marca "voy" o "me interesa". Es un **upsert**: si el usuario ya tenía un registro para ese evento, se
actualiza el `status`; si no, se crea.

**Body**:

```json
{ "status": "GOING" }
```

`status` ∈ `GOING | INTERESTED`. `204 No Content` en éxito. `404` si el evento no existe.

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

### `GET /api/admin/organizers/pending` — admin

Lista paginada de organizadores con `isVerified=false`, para que un admin los revise.

> Pendiente: este controller no tiene todavía `@PreAuthorize`/chequeo de rol — cualquier usuario
> autenticado puede llamarlo hoy. Falta restringirlo a `ROLE_ADMIN`.

**Respuesta** — `Page<OrganizerDetailDto>`.

---

### `PATCH /api/admin/organizers/{id}/verify` — admin

Aprueba al organizador: `isVerified=true`, sube el rol del usuario dueño, y le manda el email de
notificación de aprobación.

**Respuesta** — `OrganizerDetailDto`.

---

### `GET /auth/me` — requiere login

Ya existía. Devuelve `userId`, `email`, `displayName`, `role`, `avatarUrl`, y ahora también
`instagram` y `showProfilePublic`.

---

### `PATCH /auth/me` — requiere login

Actualiza el perfil social del usuario: su handle de Instagram y si quiere aparecer con nombre/foto/IG
en las listas públicas de asistentes.

**Body**:

```json
{ "instagram": "ana.baila", "showProfilePublic": true }
```

- `instagram` acepta con o sin `@` inicial (se normaliza en el backend); vacío o `null` limpia el
  campo.
- `showProfilePublic` es obligatorio en cada request (no es un PATCH parcial de un solo campo).

Devuelve el mismo shape que `GET /auth/me` con los valores actualizados.

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

- `POST /api/events` (crear evento) y `PATCH`/`DELETE` — requiere que el usuario tenga un `Organizer`
  verificado; ya existe `POST /api/organizers` para postularse.
- `POST/DELETE /api/events/{id}/favorite` (seguir/guardar evento, distinto de "voy").
- Restringir `/api/admin/organizers/**` a `ROLE_ADMIN` (hoy cualquier usuario autenticado puede
  llamarlo).
- Migraciones versionadas (Flyway/Liquibase) — hoy el schema se aplica a mano contra la BD real, sin
  registro de qué `ALTER TABLE` ya corrió en cada entorno.
