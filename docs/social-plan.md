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
| 2 | Perfil: bio, ciudad, escuela, foto propia | [ ] |
| 3 | Serate de otra persona y «Avete in comune» | [ ] |
| 4 | Denuncias, ban, silenciar | [ ] |
| 5 | Pestaña Amici: feed y sugerencias | [ ] |
| 6 | Avisos (campanita + email) e invitaciones | [ ] |
| 7 | Traguardi y @username | [ ] |

**Siguiente paso:** fase 2 del BE. Se hace todo el BE (fases 0–7) y después todo el FE. El SQL de prod se
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
- Silenciar quita a esa persona del feed y de la campanita, y ella no se entera.

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
- [ ] **Antes**: `CustomOidcUserService` pisa la foto en cada login. Guardar la de Google aparte (`google_avatar_url`) y resolver `avatarUrl` según `avatar_source` (`GOOGLE`/`CUSTOM`/`NONE`)
- [ ] SQL: `bio` (≤ 150), `attendance_visibility` (default `EVERYONE`), `school_organizer_id` + `school_name`, `origin_country`, `home_city`/`home_region`/`home_country`/`home_latitude`/`home_longitude` (los de `citta-plan.md`) + `show_home_city` (default false), columnas de avatar
- [ ] `PATCH /auth/me` y `UserDto` con los campos nuevos y sus validaciones
- [ ] `UserProfileDto` suma `bio`, escuela (id, nombre y slug, o el texto libre), `originCountry` y la ciudad solo si `show_home_city`
- [ ] «Chi ci va» respeta `attendance_visibility`: `NOBODY` no sale, `MUTUALS` solo lo ven sus amigos (sigue contando en `goingCount`)
- [ ] `PUT /rest/users/me/avatar` (multipart, máx. 5 MB, JPG/PNG/WebP, a S3 como el logo de los organizers) y `DELETE /rest/users/me/avatar` (vuelve a la de Google); «sin foto» con `avatar_source = NONE`
- [ ] `GET /rest/organizers/schools?q=` para el autocompletado
- [ ] Tests de integración · FE canvas 4b

### Fase 3 — En común
Canvas 4c: «Le sue prossime serate», «Avete in comune», «Sara e Luca ci vanno».
- [ ] `GET /rest/users/{id}/attendance` (respeta visibilidad y bloqueos)
- [ ] `GET /rest/users/{id}` suma `inCommon`: hasta 3 amigos en común con el total, serate juntos y la última
- [ ] `GET /rest/events/{id}/friends-going` (fuera de la cache)
- [ ] «Cerco qualcuno»: columna `looking_for_partner` en `event_attendance`; `POST /attendance` la acepta
- [ ] `AttendeeDto` suma `relation` y `lookingForPartner` (sin login, `relation` null)
- [ ] Tests + Postman · FE canvas 4c

### Fase 4 — Denuncias, ban, silenciar
Menú ⋯ del perfil y cola en el admin.
- [ ] Tabla `user_reports`; `POST /rest/users/{id}/report` (una abierta por pareja)
- [ ] Admin: `GET /rest/admin/reports`, `PATCH /rest/admin/reports/{id}`, `PATCH /rest/admin/users/{id}/ban` y `/unban`
- [ ] Silenciar: columna `muted` en `user_follows`; `PATCH /rest/users/{id}/follow { muted }`
- [ ] Tests + Postman · FE menú ⋯ y cola en el admin

### Fase 5 — Pestaña Amici
Canvas 4d.
- [ ] `GET /rest/users/me/feed`: «esce stasera», «ti segue», «va a X» (próximos 7 días). Sin silenciados ni bloqueados
- [ ] `GET /rest/users/me/suggestions`: gente de tus próximas serate y amigos de amigos. Sin bloqueados ni menores
- [ ] Tests + Postman · FE canvas 4d

### Fase 6 — Avisos e invitaciones
Campanita del Home e «Invita a ballare».
- [ ] Tabla `notifications`; `GET /me/notifications`, `/me/notifications/unread-count`, `PATCH /me/notifications/read`
- [ ] Tipos: nuevo seguidor, nuevo amigo, invitación. Limpieza a los 60 días con `@Scheduled`
- [ ] Emails de nuevo amigo e invitación (listener como `OrganizerEmailListener`) + columna `email_notifications`
- [ ] `POST /rest/users/{id}/invites { eventId, message }`: frase fija, no texto libre; 10 al día, 1 por persona y serata, solo serate futuras a las que vas; 429 si te pasas
- [ ] Tests + Postman · FE campanita y diálogo

### Fase 7 — Traguardi y @username
- [ ] `stats` en el perfil: serate pasadas con «Ci vado», locales distintos, primera serata
- [ ] `username` opcional y único (minúsculas, `a-z0-9._`, 3–30), propuesto desde el Instagram; `GET /rest/users/by-username/{username}`
- [ ] FE: traguardi, ruta `/@username`, tarjeta para stories (como `social-posts/cards.ts`, con el logo)

### Docs al cerrar cada fase
`API.md`, `POSTMAN_TESTING.md`, `ballastasera_schema.sql`, `user-account-deletion-plan.md` (bio, ciudad,
foto propia en S3, username y notas de denuncias). El SQL de prod va en `social-prod-db.md`, por fase.

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
