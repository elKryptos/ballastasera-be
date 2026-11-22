# Social: seguirse, amigos, bloquear, avisos — plan

Actualizado: 22/11/2026 · Rama: `feature/social` (BE y FE, desde `main`).
Diseño: canvas https://claude.ai/artifact/JqQ8CdmVDhnZENtcaNfWfK, artboards 4 (tu perfil), 4b (modifica
perfil), 4c (perfil de otra persona) y 4d (Amici).

Estados: `[ ]` no empezado · `[~]` en progreso (con nota de qué falta) · `[x]` hecho y probado (tests de integración + prueba en el FE).

## 1. Estado

| Fase | Qué | Estado |
|---|---|---|
| 0 | Rate limit en todas las APIs | [~] BE hecho con tests (22/11), falta el aviso en el FE |
| 1 | Seguir + bloquear | [~] BE hecho con tests (22/11), falta el FE |
| 2 | Perfil: bio, ciudad, escuela, foto propia | [~] BE hecho con tests (22/11), falta el FE |
| 3 | Serate de otra persona y «Avete in comune» | [~] BE hecho con tests (22/11), falta el FE |
| 4 | Denuncias, ban, silenciar | [~] BE hecho con tests (22/11), falta el FE |
| 5 | Pestaña Amici: feed y sugerencias | [ ] |
| 6 | Avisos (campanita + email) e invitaciones | [ ] |
| 7 | Traguardi y @username | [ ] |

**Siguiente paso:** fase 5 del BE. Se hace todo el BE (fases 0–7) y después todo el FE. El SQL de prod se
va juntando en `social-prod-db.md` y se corre de una vez antes del deploy.

**Tests:** desde la fase 0 hay tests de integración con Testcontainers (`support/IntegrationTest`): la app
entera contra un Postgres y un Redis reales en Docker, con el esquema de `ballastasera_schema.sql`.
Reemplazan las pruebas a mano con Postman; cada endpoint nuevo lleva el suyo. Hace falta Docker Desktop
abierto para `mvnw test`.

## 2. Lo que ya existe

El perfil que está en prod (`user-profile-plan.md`): Instagram, WhatsApp visible a todos o a nadie, fecha
de nacimiento, sexo, rol, estilos con nivel y perfil público sí/no (`GET`/`PATCH /rest/auth/me`). El perfil
de otra persona (`GET /rest/users/{id}`) hoy es un panel dentro de «Chi ci va», sin URL propia. «Ci vado»
y «Chi ci va» (`/rest/events/{id}/attendance`, `/attendees`).

Piezas que se reutilizan:
- Claves compuestas con `@EmbeddedId` + `@MapsId` (`Favorites`, `UserDanceStyles`).
- Paginación por cursor (`CursorPageDto`, como en `/users/me/attendance`).
- Subida a S3 (`S3ObjectStorageServiceImpl`, como el logo de los organizers).
- Emails con Resend: `OrganizerEmailListener` (`@TransactionalEventListener` después del commit) + `EmailService` `@Async`.
- `@EnableScheduling` activo (limpieza de avisos viejos).
- Redis ya en uso para la cache (sirve también para el rate limit).

## 3. Decisiones

**Seguir y amigos**
- Seguir es abierto: tocas «Segui» y listo, sin solicitud.
- Amigos = os seguís los dos. No hay tabla de amistad aparte.
- A un perfil privado no se le puede seguir (ya da 404).
- Los contadores (seguidores, seguidos, serate) se ven siempre.

**Privacidad**
- WhatsApp: se suma la opción «solo amigos». Los usuarios nuevos arrancan ahí; a los que ya existen no se les toca.
- Las serate a las que vas las ven todos, solo tus amigos o nadie (por defecto, todos). Con «nadie» no sales en «Chi ci va» ni en tu perfil, pero sí cuentas en el número de los que van.
- Ciudad donde vives: se elige con Photon limitado a ciudades (comuni, nunca direcciones). En el perfil solo se ve si activas «Mostra la città nel profilo», y solo el nombre («Milano»).

**Menores de 18**
- WhatsApp siempre oculto, solo los invitan sus amigos y no salen en las sugerencias. Pueden seguir y ser seguidos.

**Avisos**
- Todo sale en una campanita en la web. Los avisos se borran a los 60 días.
- Por email (Resend), solo «ora siete amici» y las invitaciones. «Ti segue» queda solo en la campanita.
- Toggle «Ricevi email» en Il tuo account. Sin push: no hay app.

**Seguridad**
- Bloquear sale en la misma entrega que seguir. Bloquear borra los follows en los dos sentidos, y ninguno ve el perfil del otro.
- Rate limit general en todas las APIs (Bucket4j + Redis), más un límite de negocio para las invitaciones.
- Denuncias: motivos fijos (perfil falso, molesto, foto inapropiada, otro + nota). Van a una cola en el admin con «Ignora» o «Banna». Nada se oculta solo.
- Silenciar quita a esa persona del feed y de la campanita, y ella no se entera. Solo se silencia a quien sigues; a un desconocido que molesta, se le bloquea.

**Perfil**
- Foto propia sin revisión previa (si es inapropiada, se denuncia). Máx. 5 MB, JPG/PNG/WebP. Se puede volver a la de Google o no poner ninguna.
- Escuela: enlace a un organizer de tipo SCHOOL o, si no está, texto libre.
- «Cerco qualcuno con cui ballare» se marca por serata, al decir «Ci vado», no en el perfil.
- Cada persona tiene su página `/persona/{id}`. El `@username` llega al final como alias para compartir.

**Feed y traguardi**
- El feed se calcula en el momento a partir de los follows y los «Ci vado». No guardamos historial, así que no hay avisos tipo «ha cambiado de nivel».
- Los traguardi cuentan los «Ci vado» de serate ya pasadas, y el texto lo dice así.

**Datos por usuario y cache:** nada que dependa de quién mira (relación, amigos que van) entra en la cache
`EVENT_DETAIL`. Va en endpoints aparte.

## 4. Fases

### Fase 0 — Rate limit
El usuario no ve nada, salvo «Troppe richieste, riprova tra poco» si abusa.
- [x] `bucket4j_jdk17-core` + `bucket4j_jdk17-lettuce` en el `pom.xml`
- [x] `RateLimitFilter` después de `JwtAuthenticationFilter`. Clave: usuario con login, IP sin login. Grupos: lectura 120/min · login 20/min · escrituras 60/min · social 30/min y 300/día · uploads 10/hora
- [x] 429 con `Retry-After` y `code: RATE_LIMITED`. Si Redis no responde se deja pasar (como la cache)
- [x] `app.rate-limit.*` en properties, con variables de entorno
- [x] Tests: filtro, servicio con Redis caído, integración con Redis real (Testcontainers). Sección en `API.md`
- [ ] FE: aviso en 429 desde el interceptor

### Fase 1 — Seguir + bloquear
Botón Segui / Ricambia / Amici, listas de amigos, WhatsApp «solo amigos», Blocca.
- [x] SQL: `MUTUALS` en `contact_visibility`, tablas `user_follows` y `user_blocks`, default de WhatsApp a `MUTUALS` (`social-prod-db.md`; dev ☐)
- [x] `POST`/`DELETE /rest/users/{id}/follow` (idempotentes, no a ti mismo, 404 si es privado o hay bloqueo)
- [x] `GET /rest/users/me/followers`, `/me/following`, `/me/friends?q=` (cursor, `UserSummaryDto`)
- [x] `POST`/`DELETE /rest/users/{id}/block`, `GET /rest/users/me/blocks`
- [x] `GET /rest/users/{id}` y `/auth/me` suman `relation` (`NONE`/`FOLLOWING`/`FOLLOWED_BY`/`MUTUAL`) y contadores
- [x] `UserMapper`: WhatsApp entre amigos; menores siempre ocultos
- [x] Bloqueos filtrados en perfil, follow, «Chi ci va» y listas
- [x] Tests de integración (uno por sitio donde un bloqueo esconde a alguien) y del mapper
- [ ] FE: página `/persona/{id}` (reutiliza `attendee-profile`), botón de follow, contadores, lista de amigos, Blocca

### Fase 2 — Perfil
Canvas 4b: bio, quién ve tus serate, escuela, ciudad y país de origen, foto propia.
- [x] **Antes**: `CustomOidcUserService` pisa la foto en cada login. Guardar la de Google aparte (`google_avatar_url`) y resolver `avatarUrl` según `avatar_source` (`GOOGLE`/`CUSTOM`/`NONE`)
- [x] SQL: `bio` (≤ 150), `attendance_visibility` (default `EVERYONE`), `school_organizer_id` + `school_name`, `origin_country`, `home_city`/`home_region`/`home_country`/`home_latitude`/`home_longitude` (los de `citta-plan.md`) + `show_home_city` (default false), columnas de avatar (`social-prod-db.md`; dev ☐)
- [x] `PATCH /auth/me` y `UserDto` con los campos nuevos y sus validaciones
- [x] `UserProfileDto` suma `bio`, escuela en campos planos (`schoolOrganizerId`, `schoolName`, `schoolSlug`), `originCountry` y `homeCityName` solo si `show_home_city`
- [x] «Chi ci va» respeta `attendance_visibility`: `NOBODY` no sale, `MUTUALS` solo lo ven sus amigos (sigue contando en `goingCount`). Las caritas del mapa, solo `EVERYONE`
- [x] `PATCH /rest/users/me/avatar` (multipart como los otros uploads, máx. 5 MB, JPG/PNG/WebP, webp en el bucket de logos con prefijo `avatars/`) y `PATCH /rest/users/me/avatar/source` `{ GOOGLE | NONE }`
- [x] `GET /rest/organizers/schools?name=` para el autocompletado
- [x] Tests de integración (perfil, escuela, «Chi ci va», foto con S3 mockeado) y del login
- [ ] FE canvas 4b

### Fase 3 — En común
Canvas 4c: «Le sue prossime serate», «Avete in comune», «Sara e Luca ci vanno».
- [x] `GET /rest/users/{id}/attendance` (respeta visibilidad y bloqueos; si no se pueden ver, lista vacía)
- [x] `GET /rest/users/{id}` suma «Avete in comune» en campos planos: hasta 3 amigos en común con el total, serate pasadas juntos y la última (solo si sus serate son visibles para quien mira)
- [x] `GET /rest/events/{id}/friends-going` (fuera de la cache)
- [x] «Cerco qualcuno»: columna `looking_for_partner` en `event_attendance`; `POST /attendance` acepta `{ lookingForPartner }` opcional (sin body no cambia) y `GET /events/{id}/attendance/me` → `{ going, lookingForPartner }`
- [x] Pallino del perfil `lookingForPartnerToday`: serata en curso o de las próximas 24 horas
- [x] `AttendeeDto` suma `relation` y `lookingForPartner` (sin login, `relation` null)
- [x] Tests de integración (SQL en `social-prod-db.md`; dev ☐)
- [ ] FE canvas 4c

### Fase 4 — Denuncias, ban, silenciar
Menú ⋯ del perfil y cola en el admin.
- [x] SQL: tabla `user_reports` (enums `report_reason`, `report_status`), `muted` en `user_follows`, `banned_at` en `users` (`social-prod-db.md`; dev ☐)
- [x] `POST /rest/users/{id}/report { reason, note }` (una abierta por pareja, idempotente; nota obligatoria con `OTHER`)
- [x] Admin: `GET /rest/admin/reports?status=`, `PATCH /rest/admin/reports/{id} { DISMISSED | BANNED }`, `PATCH /rest/admin/users/{id}/ban` y `/unban`, `GET /rest/admin/users/banned`. Banear (desde una denuncia o directo) cierra todas las abiertas contra esa persona. `ModerationService` junta denuncias y ban
- [x] El ban corta la sesión abierta: `JwtAuthenticationFilter` rechaza a los baneados (antes solo lo hacía el login)
- [x] Silenciar: `PATCH /rest/users/{id}/follow { muted }` (404 si no lo sigues); `GET /users/{id}` suma `muted`. A un desconocido no se le silencia: para eso está Blocca
- [x] Tests de integración (`ModerationIntegrationTest`, silenciar en `FollowsIntegrationTest`)
- [ ] FE menú ⋯ (Silenzia, Segnala, Blocca) y en el admin la cola de denuncias y «Utenti bannati»

### Fase 5 — Pestaña Amici
Canvas 4d.
- [ ] `GET /rest/users/me/feed`: «esce stasera», «ti segue», «va a X» (próximos 7 días). Sin silenciados ni bloqueados
- [ ] `GET /rest/users/me/suggestions`: gente de tus próximas serate y amigos de amigos. Sin bloqueados ni menores
- [ ] Tests de integración
- [ ] FE canvas 4d

### Fase 6 — Avisos e invitaciones
Campanita del Home e «Invita a ballare».
- [ ] Tabla `notifications`; `GET /me/notifications`, `/me/notifications/unread-count`, `PATCH /me/notifications/read`
- [ ] Tipos: nuevo seguidor, nuevo amigo, invitación. Limpieza a los 60 días con `@Scheduled`
- [ ] Emails de nuevo amigo e invitación (listener como `OrganizerEmailListener`) + columna `email_notifications`
- [ ] `POST /rest/users/{id}/invites { eventId, message }`: frase fija, no texto libre; 10 al día, 1 por persona y serata, solo serate futuras a las que vas; 429 si te pasas
- [ ] Tests de integración
- [ ] FE campanita y diálogo

### Fase 7 — Traguardi y @username
- [ ] `stats` en el perfil: serate pasadas con «Ci vado», locales distintos, primera serata
- [ ] `username` opcional y único (minúsculas, `a-z0-9._`, 3–30), propuesto desde el Instagram; `GET /rest/users/by-username/{username}`
- [ ] FE: traguardi, ruta `/@username`, tarjeta para stories (como `social-posts/cards.ts`, con el logo)

### Docs al cerrar cada fase
`API.md`, `ballastasera_schema.sql` (lo cargan los tests de integración), `user-account-deletion-plan.md`
(bio, ciudad, foto propia y denuncias ya están; falta username). El SQL de prod va en
`social-prod-db.md`, por fase.

## 5. Fuera por ahora
- Fotos de las serate (para eso está Instagram).
- Chat dentro de la app: el contacto sigue por WhatsApp o Instagram.
- Notificaciones push (no hay app).
- Check-in con GPS en el local, para que los traguardi cuenten asistencia real.
- Invitar a amigos que no están en ballastasera con follow automático al registrarse.
- Cambios de nivel en el feed.

## 6. A recordar
1. **Bloqueos en todas partes**: un solo método (`isBlockedEitherWay`) y un test por cada endpoint que muestra gente.
2. **Sin N+1** al calcular `relation` en listas: una query con los ids de la página.
3. **Cache**: nada del que mira en `EVENT_DETAIL`.
4. **BE y FE salen juntos**: `PATCH /auth/me` reemplaza el perfil entero, y un FE viejo borraría los campos nuevos.
5. **SQL en prod antes de cada deploy.** El `ALTER TYPE ... ADD VALUE` va solo, en su propia transacción, antes de usar el valor nuevo.
6. **Borrado de cuenta**: con borrado lógico, follows, bloqueos, avisos y denuncias se borran a mano (el `ON DELETE CASCADE` no actúa).
